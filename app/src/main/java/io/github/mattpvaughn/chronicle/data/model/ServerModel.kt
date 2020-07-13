package io.github.mattpvaughn.chronicle.data.model

import io.github.mattpvaughn.chronicle.data.sources.plex.model.Connection
import io.github.mattpvaughn.chronicle.data.sources.plex.model.PlexServer

data class ServerModel(
    val name: String,
    val connections: List<Connection>,
    val serverId: String,
    val accessToken: String = "", // access token for the server, needed for accessing shared servers
    val owned: Boolean = true
)

fun PlexServer.asServer(): ServerModel {
    return ServerModel(
        this.name,
        this.connections,
        this.clientIdentifier,
        this.accessToken ?: "",
        this.owned
    )
}


