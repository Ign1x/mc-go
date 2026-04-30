package com.mcgo.app.server

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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

class PaperServerService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ActionStop -> stopRunningServer(intent)
            ActionStart -> startPaperServer(intent)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startPaperServer(intent: Intent) {
        val server = intent.toServerCardState()
        startForeground(NotificationId, notification("正在准备 ${server.name}"))
        publish(server.id, PaperServerEventStatus.Launching, 12, "正在准备 Termux 桥接启动")
        serviceScope.launch {
            runCatching {
                ensureTermuxReady()
                publish(server.id, PaperServerEventStatus.Launching, 26, "正在解析 Paper ${server.minecraftVersion} 下载信息")
                val artifact = resolveLatestPaperDownload(server.minecraftVersion)
                publish(server.id, PaperServerEventStatus.Launching, 54, "已选择 Paper build ${artifact.build}")
                publish(server.id, PaperServerEventStatus.Launching, 78, "正在交给 Termux OpenJDK 启动")
                TermuxRunCommandBridge.startPaperServer(this@PaperServerService, server, artifact)
                publish(
                    server.id,
                    PaperServerEventStatus.Running,
                    100,
                    "已交给 Termux 运行；日志路径：${termuxServerDirectory(server.id)}/mcgo-latest.log",
                )
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager.notify(NotificationId, notification("${server.name} 已交给 Termux 运行"))
            }.onFailure { error ->
                publish(server.id, PaperServerEventStatus.Failed, 0, error.toUserFacingStartError(server.javaMajorVersion))
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopRunningServer(intent: Intent) {
        val serverId = intent.getStringExtra("id") ?: return stopSelf()
        serviceScope.launch {
            runCatching {
                ensureTermuxReady()
                TermuxRunCommandBridge.stopPaperServer(this@PaperServerService, serverId)
                publish(serverId, PaperServerEventStatus.Stopped, 0, "已向 Termux 发送停止命令")
            }.onFailure { error ->
                publish(serverId, PaperServerEventStatus.Failed, 0, error.message ?: "Termux 停止命令发送失败")
            }
            stopSelf()
        }
    }

    private fun ensureTermuxReady() {
        if (!TermuxRunCommandBridge.isTermuxInstalled(this)) {
            throw IllegalStateException("未安装 Termux。请安装 F-Droid/GitHub 版 Termux，并在 Termux 执行：pkg update && pkg install openjdk-21")
        }
        if (checkSelfPermission(TermuxRunCommandPermission) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("未授予 Termux RUN_COMMAND 权限。请在 MC-GO 设置 > 运行权限中授权 Termux 启动桥接，并在 Termux 的 ~/.termux/termux.properties 写入 allow-external-apps=true 后重启 Termux。")
        }
    }

    private fun publish(serverId: String, status: PaperServerEventStatus, progress: Int?, message: String) {
        PaperServerEvents.publish(
            PaperServerEvent(
                serverId = serverId,
                status = status,
                progress = progress,
                message = message,
            ),
        )
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

    private fun Throwable.toUserFacingStartError(javaMajorVersion: Int): String = when (this) {
        is SecurityException -> message ?: "Termux RUN_COMMAND 权限未授权"
        else -> message ?: "启动失败；请确认 Termux 已安装，并执行：${termuxJavaInstallHint(javaMajorVersion)}"
    }

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

        fun stop(context: Context, serverId: String) {
            context.startService(
                Intent(context, PaperServerService::class.java).apply {
                    action = ActionStop
                    putExtra("id", serverId)
                },
            )
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
