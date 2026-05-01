#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <unistd.h>

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

#define MCGO_LOG_TAG "MCGO-JLI"
#define MCGO_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, MCGO_LOG_TAG, __VA_ARGS__)
#define MCGO_LOGI(...) __android_log_print(ANDROID_LOG_INFO, MCGO_LOG_TAG, __VA_ARGS__)

typedef jint JLI_Launch_func(
    int argc,
    char **argv,
    int jargc,
    const char **jargv,
    int appclassc,
    const char **appclassv,
    const char *fullversion,
    const char *dotversion,
    const char *pname,
    const char *lname,
    jboolean javaargs,
    jboolean cpwildcard,
    jboolean javaw,
    jint ergo
);

using update_ld_library_path_t = void (*)(const char *);

static std::mutex g_stop_mutex;
static int g_stdin_write_fd = -1;
static bool g_launch_active = false;
static bool g_pending_stop_request = false;
static constexpr char kStopCommand[] = "stop\n";

static bool writeAll(int fd, const char *data, size_t length) {
    size_t total = 0;
    while (total < length) {
        const ssize_t written = write(fd, data + total, length - total);
        if (written < 0) {
            if (errno == EINTR) continue;
            return false;
        }
        total += static_cast<size_t>(written);
    }
    return true;
}

static void resetStopBridgeState() {
    std::lock_guard<std::mutex> lock(g_stop_mutex);
    if (g_stdin_write_fd >= 0) {
        close(g_stdin_write_fd);
        g_stdin_write_fd = -1;
    }
    g_pending_stop_request = false;
    g_launch_active = false;
}

static int prepareStopBridge() {
    int pipefd[2];
    if (pipe(pipefd) != 0) {
        MCGO_LOGE("Unable to create stdin pipe: %s", strerror(errno));
        return -13;
    }
    if (dup2(pipefd[0], STDIN_FILENO) < 0) {
        close(pipefd[0]);
        close(pipefd[1]);
        MCGO_LOGE("Unable to redirect stdin: %s", strerror(errno));
        return -14;
    }
    close(pipefd[0]);
    std::lock_guard<std::mutex> lock(g_stop_mutex);
    if (g_stdin_write_fd >= 0) {
        close(g_stdin_write_fd);
    }
    g_stdin_write_fd = pipefd[1];
    if (g_pending_stop_request) {
        if (!writeAll(g_stdin_write_fd, kStopCommand, strlen(kStopCommand))) {
            MCGO_LOGE("Unable to flush pending stop command: %s", strerror(errno));
            return -15;
        }
        g_pending_stop_request = false;
    }
    return 0;
}

static std::vector<std::string> readStringArray(JNIEnv *env, jobjectArray array) {
    std::vector<std::string> result;
    if (array == nullptr) return result;
    const jsize count = env->GetArrayLength(array);
    result.reserve(static_cast<size_t>(count));
    for (jsize index = 0; index < count; ++index) {
        auto value = static_cast<jstring>(env->GetObjectArrayElement(array, index));
        const char *utf = value == nullptr ? nullptr : env->GetStringUTFChars(value, nullptr);
        result.emplace_back(utf == nullptr ? "" : utf);
        if (utf != nullptr) {
            env->ReleaseStringUTFChars(value, utf);
        }
        if (value != nullptr) {
            env->DeleteLocalRef(value);
        }
    }
    return result;
}

static std::string readString(JNIEnv *env, jstring value) {
    if (value == nullptr) return {};
    const char *utf = env->GetStringUTFChars(value, nullptr);
    if (utf == nullptr) return {};
    std::string result(utf);
    env->ReleaseStringUTFChars(value, utf);
    return result;
}

static void updateLdLibraryPath(const std::string &value) {
    void *libdlHandle = dlopen("libdl.so", RTLD_LAZY);
    if (libdlHandle == nullptr) {
        MCGO_LOGE("Unable to open libdl.so for LD_LIBRARY_PATH update: %s", dlerror());
        return;
    }
    auto updateFn = reinterpret_cast<update_ld_library_path_t>(dlsym(libdlHandle, "android_update_LD_LIBRARY_PATH"));
    if (updateFn == nullptr) {
        dlerror();
        updateFn = reinterpret_cast<update_ld_library_path_t>(dlsym(libdlHandle, "__loader_android_update_LD_LIBRARY_PATH"));
    }
    if (updateFn != nullptr) {
        updateFn(value.c_str());
    } else {
        MCGO_LOGE("LD_LIBRARY_PATH updater symbol unavailable");
    }
    dlclose(libdlHandle);
}

static int redirectLogs(const std::string &logFile) {
    const int fd = open(logFile.c_str(), O_CREAT | O_WRONLY | O_APPEND, 0644);
    if (fd < 0) {
        MCGO_LOGE("Unable to open log file %s", logFile.c_str());
        return -10;
    }
    if (dup2(fd, STDOUT_FILENO) < 0 || dup2(fd, STDERR_FILENO) < 0) {
        close(fd);
        MCGO_LOGE("Unable to redirect stdout/stderr");
        return -11;
    }
    close(fd);
    setvbuf(stdout, nullptr, _IOLBF, 0);
    setvbuf(stderr, nullptr, _IONBF, 0);
    return 0;
}

static int applyEnvironment(const std::vector<std::string> &environment) {
    for (const auto &entry : environment) {
        const size_t separator = entry.find('=');
        if (separator == std::string::npos || separator == 0) continue;
        const std::string key = entry.substr(0, separator);
        const std::string value = entry.substr(separator + 1);
        setenv(key.c_str(), value.c_str(), 1);
        if (key == "LD_LIBRARY_PATH") {
            updateLdLibraryPath(value);
        }
    }
    return 0;
}

static int preloadLibraries(const std::vector<std::string> &libraries) {
    for (const auto &library : libraries) {
        if (library.empty()) continue;
        void *handle = dlopen(library.c_str(), RTLD_LAZY | RTLD_GLOBAL);
        if (handle == nullptr) {
            fprintf(stderr, "[MC-GO] dlopen failed: %s (%s)\n", library.c_str(), dlerror());
            MCGO_LOGE("dlopen failed for %s: %s", library.c_str(), dlerror());
            return -20;
        }
    }
    return 0;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_mcgo_app_server_PaperJvmLauncher_nativeRequestStop(
    JNIEnv * /* env */,
    jobject /* this */
) {
    std::lock_guard<std::mutex> lock(g_stop_mutex);
    if (g_stdin_write_fd >= 0) {
        return writeAll(g_stdin_write_fd, kStopCommand, strlen(kStopCommand)) ? JNI_TRUE : JNI_FALSE;
    }
    if (g_launch_active) {
        g_pending_stop_request = true;
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_mcgo_app_server_PaperJvmLauncher_nativeQueueStopRequest(
    JNIEnv * /* env */,
    jobject /* this */
) {
    std::lock_guard<std::mutex> lock(g_stop_mutex);
    g_pending_stop_request = true;
    return JNI_TRUE;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_mcgo_app_server_PaperJvmLauncher_nativeClearPendingStopRequest(
    JNIEnv * /* env */,
    jobject /* this */
) {
    std::lock_guard<std::mutex> lock(g_stop_mutex);
    g_pending_stop_request = false;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_mcgo_app_server_PaperJvmLauncher_nativeLaunchJvm(
    JNIEnv *env,
    jobject /* this */,
    jobjectArray argumentsArray,
    jobjectArray environmentArray,
    jstring workingDirectoryValue,
    jstring logFileValue,
    jstring libjliPathValue,
    jobjectArray bootstrapLibrariesArray,
    jstring launcherFullVersionValue,
    jstring launcherDotVersionValue
) {
    auto arguments = readStringArray(env, argumentsArray);
    auto environment = readStringArray(env, environmentArray);
    auto bootstrapLibraries = readStringArray(env, bootstrapLibrariesArray);
    const std::string workingDirectory = readString(env, workingDirectoryValue);
    const std::string logFile = readString(env, logFileValue);
    const std::string libjliPath = readString(env, libjliPathValue);
    const std::string launcherFullVersion = readString(env, launcherFullVersionValue);
    const std::string launcherDotVersion = readString(env, launcherDotVersionValue);
    {
        std::lock_guard<std::mutex> lock(g_stop_mutex);
        g_launch_active = true;
    }
    struct StopBridgeGuard {
        ~StopBridgeGuard() {
            resetStopBridgeState();
        }
    } stopBridgeGuard;

    if (arguments.empty()) {
        MCGO_LOGE("No JVM arguments supplied");
        return -1;
    }
    if (workingDirectory.empty()) {
        MCGO_LOGE("Working directory missing");
        return -2;
    }
    if (libjliPath.empty()) {
        MCGO_LOGE("libjli path missing");
        return -3;
    }

    const int logResult = redirectLogs(logFile);
    if (logResult != 0) {
        return logResult;
    }
    const int stopBridgeResult = prepareStopBridge();
    if (stopBridgeResult != 0) {
        return stopBridgeResult;
    }

    fprintf(stdout, "[MC-GO] launching embedded HotSpot via JLI_Launch\n");

    if (chdir(workingDirectory.c_str()) != 0) {
        fprintf(stderr, "[MC-GO] chdir failed: %s\n", strerror(errno));
        return -12;
    }
    applyEnvironment(environment);

    const int preloadResult = preloadLibraries(bootstrapLibraries);
    if (preloadResult != 0) {
        return preloadResult;
    }

    void *libjli = dlopen(libjliPath.c_str(), RTLD_LAZY | RTLD_GLOBAL);
    if (libjli == nullptr) {
        fprintf(stderr, "[MC-GO] unable to open libjli.so: %s\n", dlerror());
        return -21;
    }

    auto launch = reinterpret_cast<JLI_Launch_func *>(dlsym(libjli, "JLI_Launch"));
    if (launch == nullptr) {
        fprintf(stderr, "[MC-GO] unable to resolve JLI_Launch: %s\n", dlerror());
        return -22;
    }

    std::vector<char *> argv;
    argv.reserve(arguments.size());
    for (auto &argument : arguments) {
        argv.push_back(argument.data());
    }

    fprintf(stdout, "[MC-GO] calling JLI_Launch with %zu args\n", argv.size());
    return launch(
        static_cast<int>(argv.size()),
        argv.data(),
        0,
        nullptr,
        0,
        nullptr,
        launcherFullVersion.c_str(),
        launcherDotVersion.c_str(),
        argv.front(),
        argv.front(),
        JNI_FALSE,
        JNI_TRUE,
        JNI_FALSE,
        0
    );
}
