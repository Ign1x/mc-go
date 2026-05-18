package com.mcgo.app.ui.storage

import com.mcgo.app.ui.model.TunnelConfigFormat
import com.mcgo.app.ui.model.TunnelKind
import com.mcgo.app.ui.model.TunnelProfile
import com.mcgo.app.ui.model.TunnelSource
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

class TunnelProfileStore(
    private val storePath: Path,
) {
    fun load(): List<TunnelProfile> {
        if (!Files.exists(storePath)) return emptyList()

        val properties = Properties()
        Files.newInputStream(storePath).use { input -> properties.load(input) }
        val count = properties.getProperty("count")?.toIntOrNull() ?: return emptyList()

        return (0 until count).mapNotNull { index ->
            val prefix = "profile.$index."
            val id = properties.getProperty(prefix + "id") ?: return@mapNotNull null
            val name = properties.getProperty(prefix + "name") ?: return@mapNotNull null
            val kind = enumValueOrNull<TunnelKind>(properties.getProperty(prefix + "kind")) ?: TunnelKind.Custom
            val source = enumValueOrNull<TunnelSource>(properties.getProperty(prefix + "source")) ?: TunnelSource.ManualServer
            val format = enumValueOrNull<TunnelConfigFormat>(properties.getProperty(prefix + "format"))
            TunnelProfile(
                id = id,
                name = name,
                kind = kind,
                source = source,
                format = format,
                serverAddress = properties.getProperty(prefix + "serverAddress").orEmpty(),
                remotePort = properties.getProperty(prefix + "remotePort")?.toIntOrNull(),
                localPort = properties.getProperty(prefix + "localPort")?.toIntOrNull(),
                credentialValue = properties.getProperty(prefix + "credentialValue"),
                portRange = properties.getProperty(prefix + "portRange"),
                baseLatencyMs = properties.getProperty(prefix + "baseLatencyMs")?.toIntOrNull() ?: 0,
                currentLatencyMs = 0,
                healthLabel = "--",
                rawConfigPreview = properties.getProperty(prefix + "rawConfigPreview"),
                rawConfigText = properties.getProperty(prefix + "rawConfigText"),
                detail = properties.getProperty(prefix + "detail"),
            )
        }
    }

    fun save(profiles: List<TunnelProfile>) {
        storePath.parent?.let { parent -> Files.createDirectories(parent) }
        val properties = Properties()
        properties.setProperty("version", "1")
        properties.setProperty("count", profiles.size.toString())
        profiles.forEachIndexed { index, profile ->
            val prefix = "profile.$index."
            properties.setProperty(prefix + "id", profile.id)
            properties.setProperty(prefix + "name", profile.name)
            properties.setProperty(prefix + "kind", profile.kind.name)
            properties.setProperty(prefix + "source", profile.source.name)
            properties.setNullable(prefix + "format", profile.format?.name)
            properties.setProperty(prefix + "serverAddress", profile.serverAddress)
            properties.setNullable(prefix + "remotePort", profile.remotePort?.toString())
            properties.setNullable(prefix + "localPort", profile.localPort?.toString())
            properties.setNullable(prefix + "credentialValue", profile.credentialValue)
            properties.setNullable(prefix + "portRange", profile.portRange)
            properties.setProperty(prefix + "baseLatencyMs", profile.baseLatencyMs.toString())
            properties.setNullable(prefix + "rawConfigPreview", profile.rawConfigPreview)
            properties.setNullable(prefix + "rawConfigText", profile.rawConfigText)
            properties.setNullable(prefix + "detail", profile.detail)
        }
        storePropertiesAtomically(storePath, properties, "MC-GO tunnel profiles")
    }
}

private fun Properties.setNullable(key: String, value: String?) {
    if (value != null) setProperty(key, value)
}

private inline fun <reified T : Enum<T>> enumValueOrNull(value: String?): T? =
    enumValues<T>().firstOrNull { it.name == value }
