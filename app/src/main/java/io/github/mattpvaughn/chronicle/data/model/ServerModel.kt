package io.github.mattpvaughn.chronicle.data.model

import io.github.mattpvaughn.chronicle.data.sources.plex.model.Connection
import io.github.mattpvaughn.chronicle.data.sources.plex.model.PlexServer

data class ServerModel(
    val name: String,
    val connections: List<Connection>,
    val serverId: String,
    // Access token for the server, needed for accessing shared servers
    val accessToken: String = "",
    val owned: Boolean = true,
)

fun PlexServer.asServer(): ServerModel {
    return ServerModel(
        name = this.name,
        connections = this.connections,
        serverId = this.clientIdentifier,
        accessToken = this.accessToken ?: "",
        owned = this.owned,
    )
}
