package com.mcgo.app.server

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mcgo.app.R
import com.mcgo.app.ui.model.ServerCardState
import com.mcgo.app.ui.model.createPaperServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

class PaperServerService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var process: Process? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ActionStop -> stopRunningServer()
            ActionStart -> startPaperServer(intent)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopRunningServer()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startPaperServer(intent: Intent) {
        val server = intent.toServerCardState()
        startForeground(NotificationId, notification("正在准备 ${server.name}"))
        publish(server, PaperServerEventStatus.Launching, 12, "正在准备服务器目录")
        serviceScope.launch {
            runCatching {
                val root = filesDir.toPath().resolve("paper-servers")
                val prepared = preparePaperServerFiles(server, root)
                publish(server, PaperServerEventStatus.Launching, 22, "已写入 EULA 与 server.properties")
                if (!Files.exists(prepared.jarPath)) {
                    publish(server, PaperServerEventStatus.Launching, 28, "正在下载 Paper ${server.minecraftVersion}")
                    downloadLatestPaperJar(server.minecraftVersion, prepared.jarPath) { progress ->
                        publish(server, PaperServerEventStatus.Launching, progress.coerceIn(28, 76), "Paper 核心下载中 ${progress.coerceIn(0, 100)}%")
                    }
                } else {
                    publish(server, PaperServerEventStatus.Launching, 76, "已复用本地 Paper 核心")
                }
                val javaHome = requireManagedJavaHome(filesDir.toPath(), server.javaMajorVersion)
                publish(server, PaperServerEventStatus.Launching, 84, "正在使用 Java ${server.javaMajorVersion} 启动")
                val command = buildJavaLaunchCommand(server, prepared, javaHome)
                val runningProcess = ProcessBuilder(command)
                    .directory(prepared.workDir.toFile())
                    .redirectErrorStream(true)
                    .start()
                process = runningProcess
                publish(server, PaperServerEventStatus.Running, 100, "进程已启动，控制台日志正在接入")
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager.notify(NotificationId, notification("${server.name} 正在运行"))
                streamProcessLogs(server, runningProcess)
            }.onFailure { error ->
                publish(server, PaperServerEventStatus.Failed, 0, error.message ?: "启动失败")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private suspend fun streamProcessLogs(server: ServerCardState, runningProcess: Process) {
        withContext(Dispatchers.IO) {
            runningProcess.inputStream.bufferedReader().useLines { lines ->
                lines.take(96).forEach { line ->
                    if (line.isNotBlank()) publish(server, PaperServerEventStatus.Running, 100, line.take(160))
                }
            }
        }
        val exitCode = runningProcess.waitFor()
        if (process === runningProcess) {
            publish(server, PaperServerEventStatus.Stopped, 0, "服务器进程已退出：$exitCode")
            process = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun publish(server: ServerCardState, status: PaperServerEventStatus, progress: Int?, message: String) {
        PaperServerEvents.publish(
            PaperServerEvent(
                serverId = server.id,
                status = status,
                progress = progress,
                message = message,
            ),
        )
    }

    private fun stopRunningServer() {
        process?.destroy()
        process = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(ChannelId, "MC-GO 开服", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(text: String) = NotificationCompat.Builder(this, ChannelId)
        .setSmallIcon(R.drawable.ic_mcgo)
        .setContentTitle("MC-GO")
        .setContentText(text)
        .setOngoing(true)
        .build()

    companion object {
        private const val ChannelId = "mcgo_paper_server"
        private const val NotificationId = 2001
        private const val ActionStart = "com.mcgo.app.server.START_PAPER"
        private const val ActionStop = "com.mcgo.app.server.STOP_PAPER"

        fun start(context: Context, server: ServerCardState) {
            val intent = Intent(context, PaperServerService::class.java).apply {
                action = ActionStart
                putExtra("id", server.id)
                putExtra("name", server.name)
                putExtra("minecraftVersion", server.minecraftVersion)
                putExtra("maxPlayers", server.maxPlayers)
                putExtra("memoryMb", server.memoryMb)
                putExtra("port", server.port)
                putExtra("worldName", server.worldName)
                putExtra("javaMajorVersion", server.javaMajorVersion)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(Intent(context, PaperServerService::class.java).apply { action = ActionStop })
        }
    }
}

private fun Intent.toServerCardState(): ServerCardState = createPaperServer(
    name = getStringExtra("name").orEmpty(),
    minecraftVersion = getStringExtra("minecraftVersion") ?: "1.21.4",
    maxPlayers = getIntExtra("maxPlayers", 20),
    memoryMb = getIntExtra("memoryMb", 2048),
    port = getIntExtra("port", 25565),
    worldName = getStringExtra("worldName") ?: "world",
).let { server ->
    server.copy(
        id = getStringExtra("id") ?: "paper-server",
        javaMajorVersion = getIntExtra("javaMajorVersion", server.javaMajorVersion),
    )
}
