package com.mcgo.app.server

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import kotlin.test.Test

class PaperServerServiceStateTest {

    @Test
    fun startConflictMessage_rejectsDuplicateOrConcurrentServerStarts() {
        assertThat(startConflictMessage(currentServerId = null, requestedServerId = "alpha")).isNull()
        assertThat(startConflictMessage(currentServerId = "alpha", requestedServerId = "alpha")).contains("已在启动或运行中")
        assertThat(startConflictMessage(currentServerId = "beta", requestedServerId = "alpha")).contains("运行时槽位")
    }

    @Test
    fun foregroundNotification_opensMainActivityWhenTapped() {
        val source = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/server/PaperServerService.kt")))
        assertThat(source).contains("PendingIntent.getActivity(")
        assertThat(source).contains("Intent(this, MainActivity::class.java)")
        assertThat(source).contains("setContentIntent(")
        assertThat(source).contains("setAutoCancel(false)")
    }

    @Test
    fun readLastAppendedNonBlankLine_returnsOnlyNewlyAppendedContent() {
        val logFile = Files.createTempFile("mcgo-log-tail", ".log")
        Files.write(logFile, "\n".toByteArray())

        val first = readLastAppendedNonBlankLine(logFile, 0L)
        Files.write(logFile, "line-1\nline-2\n".toByteArray(), java.nio.file.StandardOpenOption.APPEND)
        val second = readLastAppendedNonBlankLine(logFile, first.nextOffset)
        val third = readLastAppendedNonBlankLine(logFile, second.nextOffset)

        assertThat(first.line).isNull()
        assertThat(second.line).isEqualTo("line-2")
        assertThat(third.line).isNull()
    }

    @Test
    fun stopRequestMessage_reportsGracefulShutdownRequestInsteadOfImmediateStopped() {
        assertThat(stopRequestMessage()).contains("已请求停止")
        assertThat(stopRequestMessage()).doesNotContain("已停止")
    }

    @Test
    fun resolveStopHandlingAction_cancelsPendingLaunchBeforeJvmCanAcceptStop() {
        assertThat(resolveStopHandlingAction(runtimeLaunchSubmitted = false, stopSignalDelivered = false))
            .isEqualTo(StopHandlingAction.CancelPendingLaunch)
        assertThat(resolveStopHandlingAction(runtimeLaunchSubmitted = true, stopSignalDelivered = false))
            .isEqualTo(StopHandlingAction.AwaitStopSignalDelivery)
        assertThat(resolveStopHandlingAction(runtimeLaunchSubmitted = true, stopSignalDelivered = true))
            .isEqualTo(StopHandlingAction.StopSignalAlreadyDelivered)
    }

    @Test
    fun resolveStopTargetAction_rejectsNoopAndMismatchedStopRequests() {
        assertThat(resolveStopTargetAction(currentServerId = null, requestedServerId = "survival"))
            .isEqualTo(StopTargetAction.NoActiveRuntime)
        assertThat(resolveStopTargetAction(currentServerId = "survival", requestedServerId = null))
            .isEqualTo(StopTargetAction.HandleCurrentServer)
        assertThat(resolveStopTargetAction(currentServerId = "survival", requestedServerId = "survival"))
            .isEqualTo(StopTargetAction.HandleCurrentServer)
        assertThat(resolveStopTargetAction(currentServerId = "survival", requestedServerId = "creative"))
            .isEqualTo(StopTargetAction.IgnoreMismatchedServer)
    }

    @Test
    fun noActiveRuntimeStopEvent_reportsCleanTerminalRecovery() {
        val event = noActiveRuntimeStopEvent("survival")

        assertThat(event.serverId).isEqualTo("survival")
        assertThat(event.status).isEqualTo(PaperServerEventStatus.Stopped)
        assertThat(event.message).contains("未在运行")
    }

    @Test
    fun shouldRetryQueuedStopSignal_onlyWhileSameServerStillAwaitingDelivery() {
        assertThat(
            shouldRetryQueuedStopSignal(
                currentServerId = "survival",
                serverId = "survival",
                stopRequested = true,
                stopSignalDelivered = false,
            ),
        ).isTrue()
        assertThat(
            shouldRetryQueuedStopSignal(
                currentServerId = "creative",
                serverId = "survival",
                stopRequested = true,
                stopSignalDelivered = false,
            ),
        ).isFalse()
        assertThat(
            shouldRetryQueuedStopSignal(
                currentServerId = "survival",
                serverId = "survival",
                stopRequested = false,
                stopSignalDelivered = false,
            ),
        ).isFalse()
        assertThat(
            shouldRetryQueuedStopSignal(
                currentServerId = "survival",
                serverId = "survival",
                stopRequested = true,
                stopSignalDelivered = true,
            ),
        ).isFalse()
    }

    @Test
    fun launchCancelledEvent_reportsStoppedWithoutPretendingJvmStarted() {
        val event = launchCancelledEvent("survival")

        assertThat(event.serverId).isEqualTo("survival")
        assertThat(event.status).isEqualTo(PaperServerEventStatus.Stopped)
        assertThat(event.message).contains("已取消启动")
    }

    @Test
    fun runtimeMonitorEventStatus_prefersStoppingOverRunningOrLaunching() {
        assertThat(runtimeMonitorEventStatus(runtimeRunning = false, stopRequested = false))
            .isEqualTo(PaperServerEventStatus.Launching)
        assertThat(runtimeMonitorEventStatus(runtimeRunning = true, stopRequested = false))
            .isEqualTo(PaperServerEventStatus.Running)
        assertThat(runtimeMonitorEventStatus(runtimeRunning = true, stopRequested = true))
            .isEqualTo(PaperServerEventStatus.Stopping)
        assertThat(runtimeMonitorEventStatus(runtimeRunning = false, stopRequested = true))
            .isEqualTo(PaperServerEventStatus.Stopping)
    }

    @Test
    fun runtimeExitEvent_usesGracefulStoppedOnlyAfterSuccessfulRequestedStop() {
        assertThat(runtimeExitEvent("survival", exitCode = 0, stopRequested = false, logFile = java.nio.file.Path.of("/tmp/mcgo.log")).status)
            .isEqualTo(PaperServerEventStatus.Stopped)
        assertThat(runtimeExitEvent("survival", exitCode = 0, stopRequested = true, logFile = java.nio.file.Path.of("/tmp/mcgo.log")).message)
            .contains("已安全停止")
        assertThat(runtimeExitEvent("survival", exitCode = 7, stopRequested = true, logFile = java.nio.file.Path.of("/tmp/mcgo.log")).status)
            .isEqualTo(PaperServerEventStatus.Failed)
    }

    @Test
    fun selectFrpcExitLogLine_prefersTokenMismatchOverTrailingGenericStoppedLine() {
        val line = selectFrpcExitLogLine(
            listOf(
                "[I] try to connect to server...",
                "login to the server failed: token in login doesn't match token from configuration",
                "frpc service for config file [/tmp/frpc.toml] stopped",
            ),
        )

        assertThat(line).contains("token in login doesn't match")
    }

    @Test
    fun frpcExitMessage_surfacesTokenMismatchAsActionableHint() {
        assertThat(frpcExitMessage(1, "login to the server failed: token in login doesn't match token from configuration"))
            .contains("FRP token 不匹配")
        assertThat(frpcExitMessage(1, "login to the server failed: token in login doesn't match token from configuration"))
            .contains("token")
    }

    @Test
    fun javaRuntimeMayRequireFreshProcess_restartsWhenJavaMajorChanges() {
        assertThat(javaRuntimeMayRequireFreshProcess(previousJavaMajorVersion = null, nextJavaMajorVersion = 8)).isEqualTo(false)
        assertThat(javaRuntimeMayRequireFreshProcess(previousJavaMajorVersion = 17, nextJavaMajorVersion = 17)).isEqualTo(false)
        assertThat(javaRuntimeMayRequireFreshProcess(previousJavaMajorVersion = 17, nextJavaMajorVersion = 8)).isEqualTo(true)
        assertThat(javaRuntimeMayRequireFreshProcess(previousJavaMajorVersion = 21, nextJavaMajorVersion = 11)).isEqualTo(true)
    }

    private fun projectRoot(): java.nio.file.Path =
        generateSequence(java.nio.file.Path.of(".").toAbsolutePath().normalize()) { it.parent }
            .firstOrNull { Files.exists(it.resolve("app/build.gradle.kts")) }
            ?: error("project root not found")
}
