package com.mcgo.app.server

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import kotlin.test.Test

class PaperServerServiceStateTest {

    @Test
    fun startConflictMessage_rejectsDuplicateOrConcurrentServerStarts() {
        assertThat(startConflictMessage(currentServerId = null, requestedServerId = "alpha")).isNull()
        assertThat(startConflictMessage(currentServerId = "alpha", requestedServerId = "alpha")).contains("已在启动或运行中")
        assertThat(startConflictMessage(currentServerId = "beta", requestedServerId = "alpha")).contains("单服运行")
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
}
