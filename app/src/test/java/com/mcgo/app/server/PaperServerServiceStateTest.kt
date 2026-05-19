package com.mcgo.app.server

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import kotlin.test.Test

class PaperServerServiceStateTest {

    @Test
    fun serviceRuntimeStateHelpers_liveOutsideAndroidServiceClass() {
        val serviceSource = readSource("app/src/main/java/com/mcgo/app/server/PaperServerService.kt")
        val stateSource = readSource("app/src/main/java/com/mcgo/app/server/PaperServerServiceRuntimeState.kt")

        listOf(
            "enum class StopTargetAction",
            "enum class CommandTargetAction",
            "fun startConflictMessage(",
            "fun resolveStopTargetAction(",
            "fun resolveCommandTargetAction(",
            "enum class StopHandlingAction",
            "fun runtimeMonitorEventStatus(",
            "fun updatedOnlinePlayersFromLogLine(",
            "fun runtimeExitEvent(",
            "fun pendingTunnelBindingForFrpcPlan(",
            "data class FrpcReadinessSignal(",
            "fun selectFrpcReadinessSignal(",
            "fun frpcExitMessage(",
        ).forEach { oldDefinition ->
            assertThat(serviceSource).doesNotContain(oldDefinition)
        }
        assertThat(serviceSource).contains("startConflictMessage(currentServerId = currentServerId, requestedServerId = server.id)")
        assertThat(serviceSource).contains("runtimeExitEvent(")
        assertThat(serviceSource).contains("selectFrpcReadinessSignal(tail.lines)")
        assertThat(stateSource).contains("enum class StopTargetAction")
        assertThat(stateSource).contains("fun updatedOnlinePlayerNamesFromLogLine(")
        assertThat(stateSource).contains("fun frpcReadinessMessage(")
        assertThat(stateSource).contains("token in login doesn't match token from configuration")
    }

    @Test
    fun serviceIntentExtraDecoders_liveOutsideAndroidServiceClass() {
        val serviceSource = readSource("app/src/main/java/com/mcgo/app/server/PaperServerService.kt")
        val extrasSource = readSource("app/src/main/java/com/mcgo/app/server/PaperServerServiceIntentExtras.kt")

        listOf(
            "internal fun decodeServerCardStateExtrasForTest(",
            "internal fun decodeTunnelProfileExtrasForTest(",
            "internal fun decodeTunnelProfilesExtrasForTest(",
            "internal fun hydrateLaunchTunnelProfilesForTest(",
            "private fun Intent.toServerCardState(",
            "private fun decodeServerCardStateExtras(",
            "private fun Intent.toTunnelProfiles(",
            "private fun decodeTunnelProfilesExtras(",
            "private fun hydrateLaunchTunnelProfiles(",
            "private fun Intent.toTunnelProfile(",
            "private fun decodeTunnelProfileExtras(",
        ).forEach { oldDefinition -> assertThat(serviceSource).doesNotContain(oldDefinition) }
        assertThat(serviceSource).contains("val server = intent.toServerCardState()")
        assertThat(serviceSource).contains("launchProfiles = intent.toTunnelProfiles()")
        assertThat(extrasSource).contains("internal fun Intent.toServerCardState(): ServerCardState")
        assertThat(extrasSource).contains("internal fun Intent.toTunnelProfiles(): List<TunnelProfile>")
        assertThat(extrasSource).contains("internal fun Intent.toTunnelProfile(): TunnelProfile?")
        assertThat(extrasSource).contains("fun decodeServerCardStateExtras(")
        assertThat(extrasSource).contains("createNeoForgeServer(")
        assertThat(extrasSource).contains("rawConfigText")
    }

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
    fun startIntent_marksExplicitPreparedWorkspacePathAsPrepared() {
        val source = readSource("app/src/main/java/com/mcgo/app/server/PaperServerService.kt")
        val startSignature = "fun start(\n            context: Context,\n            server: ServerCardState,\n            tunnels: List<TunnelProfile> = emptyList(),\n            workspacePath: String? = null,"
        assertThat(source).contains(startSignature)
        val startOverload = source
            .substringAfter(startSignature)
            .substringBefore("\n        fun stop(")

        assertThat(startOverload).contains("putExtra(\"workspacePath\", workspacePath)")
        assertThat(startOverload).contains("putExtra(\"workspaceMode\", workspaceMode?.name)")
        assertThat(startOverload).contains("putExtra(\"workspacePrepared\", workspacePath != null)")
        assertThat(startOverload).doesNotContain("putExtra(\"workspacePrepared\", server.runtimeLogPath != null)")
    }

    @Test
    fun serviceLaunchFlow_holdsPartialWakeLockUntilRuntimeCleanup() {
        val source = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/server/PaperServerService.kt")))

        assertThat(source).contains("import android.os.PowerManager")
        assertThat(source).contains("private var runtimeWakeLock: PowerManager.WakeLock? = null")
        assertThat(source).contains("acquireRuntimeWakeLock(server.id)")
        assertThat(source).contains("PowerManager.PARTIAL_WAKE_LOCK")
        assertThat(source).contains("setReferenceCounted(false)")
        assertThat(source).contains("private fun releaseRuntimeWakeLock()")
        assertThat(source).contains("releaseRuntimeWakeLock()")
        assertThat(source.indexOf("startForeground(notificationId(), notification(\"正在启动 ${'$'}{server.name}\"))"))
            .isLessThan(source.indexOf("acquireRuntimeWakeLock(server.id)"))
        assertThat(source.indexOf("releaseRuntimeWakeLock()"))
            .isLessThan(source.indexOf("stopForeground(STOP_FOREGROUND_REMOVE)"))
    }

    @Test
    fun serviceLaunchFlow_tracksIndependentFrpcProcessesForMultipleTunnels() {
        val source = readSource("app/src/main/java/com/mcgo/app/server/PaperServerService.kt")
        val stateSource = readSource("app/src/main/java/com/mcgo/app/server/PaperServerServiceRuntimeState.kt")
        val extrasSource = readSource("app/src/main/java/com/mcgo/app/server/PaperServerServiceIntentExtras.kt")

        assertThat(source).contains("private val frpcProcesses = mutableMapOf<String, Process>()")
        assertThat(source).contains("private val frpcWatchJobs = mutableMapOf<String, Job>()")
        assertThat(source).contains("private val tunnelRuntimeStateLock = Any()")
        assertThat(source).contains("synchronized(tunnelRuntimeStateLock)")
        assertThat(source).contains("if (!isActive) return@launch")
        assertThat(extrasSource).contains("internal fun Intent.toTunnelProfiles(): List<TunnelProfile>")
        assertThat(source).contains("val tunnelPlans = tunnelRuntimePlansForStart(")
        assertThat(source).contains("startFrpcForPlans(server, tunnelPlans)")
        assertThat(source).contains("frpcProcesses[plan.tunnelId] = process")
        assertThat(source).contains("frpcWatchJobs[plan.tunnelId] = serviceScope.launch")
        assertThat(stateSource).contains("tunnelId = plan.tunnelId")
        assertThat(source).contains("currentTunnelBindings = tunnelPlans.map")
        assertThat(source).contains("startFrpcForPlans(server, tunnelPlans)")
        assertThat(source.indexOf("currentTunnelBindings = tunnelPlans.map")).isLessThan(source.indexOf("startFrpcForPlans(server, tunnelPlans)"))
        assertThat(source).contains("managedPaperServerFrpcLogFile(filesDir.toPath(), server.id, plan.tunnelId)")
        assertThat(source).contains("putExtra(\"tunnelCount\", tunnels.size)")
        assertThat(source).doesNotContain("frpcProcesses[plan.displayLabel]")
        assertThat(source).doesNotContain("putExtra(\"tunnels.\$index.credentialValue\"")
    }

    @Test
    fun logTailMonitor_processesEveryAppendedLineSoJoinSignalsAreNotDroppedBehindLoginLine() {
        val source = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/server/PaperServerService.kt")))

        assertThat(source).contains("val tail = readAppendedNonBlankLinesWithOffset(logFile, logOffset)")
        assertThat(source).contains("logOffset = tail.nextOffset")
        assertThat(source).contains("tail.lines.forEach { line ->")
        assertThat(source).doesNotContain("Files.size(logFile)")
    }

    @Test
    fun logTailHelpers_useBoundedNoFollowReadsForAppendedSegments() {
        val source = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/server/LogTailing.kt")))

        assertThat(source).contains("isReadableLogTailFile(logFile)")
        assertThat(source).contains("Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)")
        assertThat(source).contains("Files.newByteChannel(logFile, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)")
        assertThat(source).contains("val fileSize = channel.size()")
        assertThat(source).contains("readLogBytesAt(channel, window.startOffset, window.bytesToRead)")
        assertThat(source).contains("channel.position(offset)")
        assertThat(source).contains("startsAtLogLineBoundary(channel, window.startOffset)")
        assertThat(source).contains("completeAppendedLogChunk(")
        assertThat(source).contains("appendedLogReadWindow(fileSize, previousOffset)")
        assertThat(source).doesNotContain("Files.size(logFile)")
        assertThat(source).doesNotContain("Files.isRegularFile(logFile)")
        assertThat(source).doesNotContain("Files.newByteChannel(logFile).use")
        assertThat(source).doesNotContain("(fileSize - startOffset).toInt().coerceAtLeast(0)")
        assertThat(source).doesNotContain("input.readBytes()")
    }

    @Test
    fun logTailHelpers_rejectSymbolicLinkLogFiles() {
        val realLog = Files.createTempFile("mcgo-real-log", ".log")
        val symlinkLog = Files.createTempFile("mcgo-symlink-log", ".log")
        Files.write(realLog, "outside-secret\n".toByteArray())
        Files.deleteIfExists(symlinkLog)
        Files.createSymbolicLink(symlinkLog, realLog)

        val lines = readAppendedNonBlankLinesWithOffset(symlinkLog, previousOffset = 42L)
        val lastLine = readLastAppendedNonBlankLine(symlinkLog, previousOffset = 42L)
        val matchingLine = readLastAppendedMatchingLine(symlinkLog, previousOffset = 42L) { true }

        assertThat(lines).isEqualTo(AppendedLinesResult(nextOffset = 42L, lines = emptyList()))
        assertThat(lastLine).isEqualTo(LogTailResult(nextOffset = 42L, line = null))
        assertThat(matchingLine).isEqualTo(LogTailResult(nextOffset = 42L, line = null))
    }

    @Test
    fun appendedLogReadWindow_capsHugeAppendedSegmentsWithoutOverflow() {
        val fileSize = Int.MAX_VALUE.toLong() + 8192L

        val window = appendedLogReadWindow(fileSize = fileSize, previousOffset = 0L, maxBytes = 4096)

        assertThat(window).isNotNull()
        assertThat(window?.startOffset).isEqualTo(0L)
        assertThat(window?.bytesToRead).isEqualTo(4096 + MaxAppendedLogLineContinuationBytes)
        assertThat(window?.nextOffset).isEqualTo(4096L + MaxAppendedLogLineContinuationBytes)
    }

    @Test
    fun appendedLogReadWindow_keepsSmallAppendedSegmentsAndReportsNoNewData() {
        assertThat(appendedLogReadWindow(fileSize = 128L, previousOffset = 32L, maxBytes = 4096))
            .isEqualTo(AppendedLogReadWindow(startOffset = 32L, bytesToRead = 96, nextOffset = 128L))
        assertThat(appendedLogReadWindow(fileSize = 128L, previousOffset = 128L, maxBytes = 4096)).isNull()
        assertThat(appendedLogReadWindow(fileSize = 128L, previousOffset = 256L, maxBytes = 4096)).isNull()
    }

    @Test
    fun appendedLogChunk_decodesOnlyCompleteLinesUnlessWindowReachedEnd() {
        val partialChunk = completeAppendedLogChunk(
            bytes = "line-1\npartial-line".toByteArray(),
            startOffset = 10L,
            reachedEnd = false,
        )
        val finalChunk = completeAppendedLogChunk(
            bytes = "tail-without-newline".toByteArray(),
            startOffset = 100L,
            reachedEnd = true,
        )

        assertThat(partialChunk).isEqualTo(
            AppendedLogChunk(nextOffset = 17L, lines = listOf("line-1")),
        )
        assertThat(finalChunk).isEqualTo(
            AppendedLogChunk(nextOffset = 120L, lines = listOf("tail-without-newline")),
        )
    }

    @Test
    fun appendedLogChunk_advancesPastOverlongNewlineFreePartialWindow() {
        val chunk = completeAppendedLogChunk(
            bytes = "unterminated-overlong-fragment".toByteArray(),
            startOffset = 256L,
            reachedEnd = false,
        )

        assertThat(chunk).isEqualTo(
            AppendedLogChunk(
                nextOffset = 256L + "unterminated-overlong-fragment".toByteArray().size,
                lines = emptyList(),
            ),
        )
    }

    @Test
    fun appendedLogChunk_discardsLeadingPartialLineWhenResumeOffsetWasMidLine() {
        val chunk = completeAppendedLogChunk(
            bytes = "suffix-of-skipped-line\nnext-line\n".toByteArray(),
            startOffset = 512L,
            reachedEnd = false,
            discardLeadingPartialLine = true,
        )

        assertThat(chunk).isEqualTo(
            AppendedLogChunk(
                nextOffset = 512L + "suffix-of-skipped-line\nnext-line\n".toByteArray().size,
                lines = listOf("next-line"),
            ),
        )
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
    fun installerBootstrapSetupCompletedEvent_reportsStoppedAndPromptsSecondStart() {
        val event = installerBootstrapSetupCompletedEvent("survival")

        assertThat(event.serverId).isEqualTo("survival")
        assertThat(event.status).isEqualTo(PaperServerEventStatus.Stopped)
        assertThat(event.message).contains("请再次点击启动")
    }

    @Test
    fun installerBootstrapSetupCompletion_shortCircuitsJvmLaunchUntilUserStartsAgain() {
        val source = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/server/PaperServerService.kt")))

        assertThat(source).contains("installerBootstrapSetupCompletedEvent(server.id)")
        assertThat(source).contains("return@runCatching")
        assertThat(source.indexOf("installerBootstrapSetupCompletedEvent(server.id)")).isLessThan(source.indexOf("val launchConfig = buildManagedPaperLaunchConfig("))
    }

    @Test
    fun installerBootstrapSetupOutput_isForwardedToManagedRuntimeLogAndLaunchingEvents() {
        val source = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/server/PaperServerService.kt")))

        assertThat(source).contains("logFile = managedPaperServerLogFile(filesDir.toPath(), server.id)")
        assertThat(source).contains("onOutputLine = { line ->")
        assertThat(source).contains("publish(")
        assertThat(source).contains("line.takeLast(280)")
    }

    @Test
    fun serviceLaunchFlow_appendsStructuredDebugMarkersToManagedRuntimeLog() {
        val source = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/server/PaperServerService.kt")))

        assertThat(source).contains("appendManagedServerDebugLog(")
        assertThat(source).contains("\"启动请求已接收\"")
        assertThat(source).contains("\"工作目录已就绪\"")
        assertThat(source).contains("\"运行时上下文已准备\"")
        assertThat(source).contains("\"JVM 启动参数已生成\"")
        assertThat(source).contains("\"运行时已退出\"")
    }

    @Test
    fun serviceControlFlow_appendsDebugMarkersForStopAndConsoleCommands() {
        val source = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/server/PaperServerService.kt")))

        assertThat(source).contains("\"停止请求已接收\"")
        assertThat(source).contains("\"控制台指令已提交\"")
        assertThat(source).contains("\"控制台指令发送失败\"")
        assertThat(source).doesNotContain("commandPreview")
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
    fun updatedOnlinePlayersFromLogLine_tracksJoinLeaveWithoutDoubleCountingLoginPrelude() {
        assertThat(updatedOnlinePlayersFromLogLine(0, "[17:50:21 INFO]: UUID of player ign1xx is 711240d6-28f0-4e1a-9ab7-b07f3f7348a3")).isNull()
        assertThat(updatedOnlinePlayersFromLogLine(0, "[17:50:27 INFO]: ign1xx[/127.0.0.1:34840] logged in with entity id 17 at ([minecraft:overworld]104.5, 63.0, 233.5)")).isNull()
        assertThat(updatedOnlinePlayersFromLogLine(0, "[17:50:27 INFO]: ign1xx joined the game")).isEqualTo(1)
        assertThat(updatedOnlinePlayersFromLogLine(1, "[17:55:10 INFO]: ign1xx left the game")).isEqualTo(0)
    }

    @Test
    fun updatedOnlinePlayersFromLogLine_ignoresChatAndPluginLinesThatOnlyContainJoinPhrases() {
        assertThat(updatedOnlinePlayersFromLogLine(1, "[17:50:27 INFO]: <ign1xx> joined the game")).isNull()
        assertThat(updatedOnlinePlayersFromLogLine(1, "[17:50:27 INFO]: [MyPlugin] somebody joined the game")).isNull()
        assertThat(updatedOnlinePlayersFromLogLine(1, "[17:50:27 INFO]: MyPlugin: somebody joined the game")).isNull()
        assertThat(updatedOnlinePlayersFromLogLine(1, "joined the game")).isNull()
    }

    @Test
    fun updatedOnlinePlayerNamesFromLogLine_tracksJoinAndLeavePlayerNames() {
        assertThat(updatedOnlinePlayerNamesFromLogLine(emptyList(), "[17:50:27 INFO]: ign1xx joined the game")?.toList())
            .containsExactly("ign1xx")
        assertThat(updatedOnlinePlayerNamesFromLogLine(listOf("ign1xx", "paimon"), "[17:55:10 INFO]: ign1xx left the game")?.toList())
            .containsExactly("paimon")
    }

    @Test
    fun updatedOnlinePlayerNamesFromLogLine_ignoresChatPluginAndLoginPreludeLines() {
        assertThat(updatedOnlinePlayerNamesFromLogLine(listOf("paimon"), "[17:50:27 INFO]: <ign1xx> joined the game")).isNull()
        assertThat(updatedOnlinePlayerNamesFromLogLine(listOf("paimon"), "[17:50:27 INFO]: [MyPlugin] ign1xx joined the game")).isNull()
        assertThat(updatedOnlinePlayerNamesFromLogLine(listOf("paimon"), "[17:50:27 INFO]: ign1xx[/127.0.0.1:34840] logged in with entity id 17 at ([minecraft:overworld]104.5, 63.0, 233.5)")).isNull()
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
    fun selectFrpcReadinessSignal_waitsForProxySuccessAndPrioritizesFailures() {
        assertThat(
            selectFrpcReadinessSignal(
                listOf("[I] login to server success, get run id [abcd]"),
            ),
        ).isNull()
        assertThat(
            selectFrpcReadinessSignal(
                listOf("[I] [control.go:123] [minecraft] start proxy success"),
            )?.status,
        ).isEqualTo(FrpcReadinessStatus.Ready)
        assertThat(
            selectFrpcReadinessSignal(
                listOf(
                    "[I] login to server success, get run id [abcd]",
                    "[E] [minecraft] start proxy error: port already used",
                ),
            )?.status,
        ).isEqualTo(FrpcReadinessStatus.Failed)
    }

    @Test
    fun frpcReadinessMessage_surfacesActionableFailureReasons() {
        val tokenMismatch = selectFrpcReadinessSignal(
            listOf("login to the server failed: token in login doesn't match token from configuration"),
        )!!
        assertThat(tokenMismatch.status).isEqualTo(FrpcReadinessStatus.Failed)
        assertThat(frpcReadinessMessage("家庭 FRP", "frp.example.com:39001", tokenMismatch))
            .contains("token 不匹配")

        val portConflict = selectFrpcReadinessSignal(
            listOf("start proxy error: port already used"),
        )!!
        assertThat(portConflict.status).isEqualTo(FrpcReadinessStatus.Failed)
        assertThat(frpcReadinessMessage("家庭 FRP", "frp.example.com:39001", portConflict))
            .contains("远端端口可能已被占用")
    }

    @Test
    fun pendingTunnelBindingForFrpcPlan_hidesPublicAddressUntilReady() {
        val plan = TunnelRuntimePlan(
            tunnelId = "frp-home",
            binaryPath = java.nio.file.Path.of("/tmp/libfrpc.so"),
            extractedBinaryPath = java.nio.file.Path.of("/tmp/frpc"),
            configPath = java.nio.file.Path.of("/tmp/frpc.toml"),
            configText = "serverAddr = \"frp.example.com\"",
            displayLabel = "家庭 FRP",
            runtimeAddress = "frp.example.com:39001",
            remotePort = 39001,
        )

        val pending = pendingTunnelBindingForFrpcPlan(plan)
        assertThat(pending.tunnelId).isEqualTo("frp-home")
        assertThat(pending.remotePort).isEqualTo(39001)
        assertThat(pending.activeLabel).isNull()
        assertThat(pending.runtimeAddress).isNull()

        val ready = readyTunnelBindingForFrpcPlan(plan)
        assertThat(ready.activeLabel).isEqualTo("家庭 FRP")
        assertThat(ready.runtimeAddress).isEqualTo("frp.example.com:39001")
    }

    @Test
    fun serviceLaunchFlow_waitsForFrpcReadinessLogBeforePublishingPublicAddress() {
        val source = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/server/PaperServerService.kt")))

        assertThat(source).contains("tunnelPlans.map(::pendingTunnelBindingForFrpcPlan)")
        assertThat(source).contains("selectFrpcReadinessSignal(tail.lines)")
        assertThat(source).contains("while (isActive && process.isAlive")
        assertThat(source).doesNotContain("FRP 隧道已启动，等待服务器绑定端口")
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
    fun frpcExitMessage_surfacesProxyPortConflictAsActionableHint() {
        val line = selectFrpcExitLogLine(
            listOf(
                "[I] login to server success, get run id [abcd]",
                "[E] [minecraft] start proxy error: port already used",
                "frpc service for config file [/tmp/frpc.toml] stopped",
            ),
        )

        assertThat(line).contains("start proxy error")
        assertThat(frpcExitMessage(1, line)).contains("远端端口可能已被占用")
    }

    @Test
    fun javaRuntimeMayRequireFreshProcess_restartsWhenJavaMajorChanges() {
        assertThat(javaRuntimeMayRequireFreshProcess(previousJavaMajorVersion = null, nextJavaMajorVersion = 8)).isEqualTo(false)
        assertThat(javaRuntimeMayRequireFreshProcess(previousJavaMajorVersion = 17, nextJavaMajorVersion = 17)).isEqualTo(false)
        assertThat(javaRuntimeMayRequireFreshProcess(previousJavaMajorVersion = 17, nextJavaMajorVersion = 8)).isEqualTo(true)
        assertThat(javaRuntimeMayRequireFreshProcess(previousJavaMajorVersion = 21, nextJavaMajorVersion = 11)).isEqualTo(true)
    }

    private fun readSource(relativePath: String): String =
        String(Files.readAllBytes(projectRoot().resolve(relativePath)))

    private fun projectRoot(): java.nio.file.Path =
        generateSequence(java.nio.file.Path.of(".").toAbsolutePath().normalize()) { it.parent }
            .firstOrNull { Files.exists(it.resolve("app/build.gradle.kts")) }
            ?: error("project root not found")
}
