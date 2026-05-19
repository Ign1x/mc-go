package com.mcgo.app.server

import com.mcgo.app.ui.model.ServerCardState

fun buildPaperEula(): String = "eula=true\n"

fun buildServerProperties(server: ServerCardState): String =
    mergeManagedServerProperties(
        managedProperties = buildManagedServerProperties(server),
        overrideProperties = server.serverPropertiesOverride,
    )

fun buildManagedServerProperties(server: ServerCardState): String = buildString {
    appendLine("server-port=${server.port}")
    appendLine("level-name=${server.worldName.asServerPropertyValue()}")
    appendLine("max-players=${server.maxPlayers}")
    appendLine("motd=${server.name.asServerPropertyValue()}")
    appendLine("gamemode=${server.gameMode.propertyValue}")
    appendLine("difficulty=${server.difficulty.propertyValue}")
    appendLine("online-mode=${server.onlineMode}")
    appendLine("pvp=${server.pvpEnabled}")
    appendLine("enable-command-block=true")
    appendLine("allow-flight=true")
    appendLine("view-distance=8")
    appendLine("simulation-distance=4")
}

private fun String.asServerPropertyValue(): String = trim()
    .replace('\\', '/')
    .replace('\r', ' ')
    .replace('\n', ' ')
    .replace('=', '-')
    .replace(':', '-')
    .ifBlank { "MC-GO Server" }

private val RuntimeOwnedServerPropertyKeys = setOf(
    "server-port",
)

private fun mergeManagedServerProperties(
    managedProperties: String,
    overrideProperties: String?,
): String {
    val overrides = overrideProperties
        ?.lineSequence()
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() && !it.startsWith("#") }
        ?.mapNotNull { line ->
            val separatorIndex = line.indexOf('=')
            if (separatorIndex <= 0) return@mapNotNull null
            val key = line.substring(0, separatorIndex).trim()
            val value = line.substring(separatorIndex + 1).trim()
            key to value
        }
        ?.filterNot { (key, _) -> key in RuntimeOwnedServerPropertyKeys }
        ?.toMap(linkedMapOf())
        .orEmpty()

    val merged = linkedMapOf<String, String>()
    managedProperties.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .forEach { line ->
            val separatorIndex = line.indexOf('=')
            if (separatorIndex <= 0) return@forEach
            val key = line.substring(0, separatorIndex).trim()
            val managedValue = line.substring(separatorIndex + 1).trim()
            merged[key] = overrides[key] ?: managedValue
        }
    overrides.forEach { (key, value) ->
        if (key !in merged) merged[key] = value
    }
    return merged.entries.joinToString(separator = "\n", postfix = "\n") { (key, value) -> "$key=$value" }
}
