package io.github.mattpvaughn.chronicle.data.model

import io.github.mattpvaughn.chronicle.data.plex.model.Connection
import io.github.mattpvaughn.chronicle.data.plex.model.Server

data class ServerModel(val name: String, val connections: List<Connection>, val serverId: String)

fun Server.asServer(): ServerModel {
    return ServerModel(this.name, this.connections, this.clientIdentifier)
}


