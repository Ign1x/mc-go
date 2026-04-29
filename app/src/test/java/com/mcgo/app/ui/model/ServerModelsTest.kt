package com.mcgo.app.ui.model

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

class ServerModelsTest {

    @Test
    fun createPaperServer_buildsVanillaPaperInstanceWithRecommendedJava() {
        val server = createPaperServer(
            name = "生存服",
            minecraftVersion = "1.21.4",
            maxPlayers = 20,
            memoryMb = 2048,
        )

        assertThat(server.name).isEqualTo("生存服")
        assertThat(server.serverType).isEqualTo(MinecraftServerType.Paper)
        assertThat(server.edition).isEqualTo("Paper 1.21.4")
        assertThat(server.minecraftVersion).isEqualTo("1.21.4")
        assertThat(server.javaMajorVersion).isEqualTo(21)
        assertThat(server.worldName).isEqualTo("world")
        assertThat(server.defaultPort).isEqualTo(25565)
        assertThat(server.maxPlayers).isEqualTo(20)
        assertThat(server.memoryLabel).isEqualTo("2.0 GB RAM")
        assertThat(server.memoryMb).isEqualTo(2048)
        assertThat(server.id).startsWith("server-")
        assertThat(server.isOnline).isFalse()
        assertThat(server.launchStatus).isEqualTo(ServerLaunchStatus.Ready)
    }

    @Test
    fun startPaperServer_marksServerRunningAndBuildsLaunchPlan() {
        val server = createPaperServer(
            name = "生存服",
            minecraftVersion = "1.21.4",
            maxPlayers = 20,
            memoryMb = 2048,
        )

        val started = server.startPaperServer(tunnel = null, startupPort = 25566)

        assertThat(started.isOnline).isTrue()
        assertThat(started.port).isEqualTo(25566)
        assertThat(started.launchStatus).isEqualTo(ServerLaunchStatus.Running)
        assertThat(started.launchPlan?.serverJarName).isEqualTo("paper-1.21.4.jar")
        assertThat(started.launchPlan?.javaMajorVersion).isEqualTo(21)
        assertThat(started.launchPlan?.arguments).contains("-Xmx2048M")
        assertThat(started.launchPlan?.arguments).contains("nogui")
        assertThat(started.activeTunnelLabel).isNull()
    }
}
