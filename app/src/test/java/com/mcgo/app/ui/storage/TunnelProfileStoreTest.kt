package com.mcgo.app.ui.storage

import com.google.common.truth.Truth.assertThat
import com.mcgo.app.ui.model.TunnelConfigFormat
import com.mcgo.app.ui.model.TunnelKind
import com.mcgo.app.ui.model.TunnelProfile
import com.mcgo.app.ui.model.TunnelSource
import java.nio.file.Files
import kotlin.test.Test

class TunnelProfileStoreTest {

    @Test
    fun load_returnsEmptyListWhenStoreFileDoesNotExist() {
        val storePath = Files.createTempDirectory("mcgo-tunnel-store-empty").resolve("tunnels.properties")
        val store = TunnelProfileStore(storePath)

        assertThat(store.load()).isEmpty()
    }

    @Test
    fun saveAndLoad_roundTripsUserConfigButResetsRuntimeLatencyState() {
        val storePath = Files.createTempDirectory("mcgo-tunnel-store-roundtrip").resolve("tunnels.properties")
        val store = TunnelProfileStore(storePath)
        val profile = TunnelProfile(
            id = "frp-home",
            name = "家庭 FRP",
            kind = TunnelKind.Frp,
            source = TunnelSource.ManualServer,
            format = TunnelConfigFormat.Toml,
            serverAddress = "frp.home:7000",
            remotePort = 39001,
            localPort = 25565,
            credentialValue = "secret-token",
            portRange = "39000-39100",
            baseLatencyMs = 0,
            currentLatencyMs = 72,
            healthLabel = "稳定",
            rawConfigPreview = "serverAddr = \"frp.home\"",
            rawConfigText = "serverAddr = \"frp.home\"\nserverPort = 7000",
            detail = "FRP 服务器参数 · Token 已保存",
        )

        store.save(listOf(profile))
        val loaded = store.load()

        assertThat(loaded).hasSize(1)
        assertThat(loaded.single().id).isEqualTo("frp-home")
        assertThat(loaded.single().name).isEqualTo("家庭 FRP")
        assertThat(loaded.single().kind).isEqualTo(TunnelKind.Frp)
        assertThat(loaded.single().source).isEqualTo(TunnelSource.ManualServer)
        assertThat(loaded.single().format).isEqualTo(TunnelConfigFormat.Toml)
        assertThat(loaded.single().serverAddress).isEqualTo("frp.home:7000")
        assertThat(loaded.single().remotePort).isEqualTo(39001)
        assertThat(loaded.single().localPort).isEqualTo(25565)
        assertThat(loaded.single().credentialValue).isEqualTo("secret-token")
        assertThat(loaded.single().portRange).isEqualTo("39000-39100")
        assertThat(loaded.single().rawConfigText).isEqualTo("serverAddr = \"frp.home\"\nserverPort = 7000")
        assertThat(loaded.single().currentLatencyMs).isEqualTo(0)
        assertThat(loaded.single().healthLabel).isEqualTo("检测中")
    }
}
