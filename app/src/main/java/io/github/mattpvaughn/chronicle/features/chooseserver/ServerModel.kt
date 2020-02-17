package io.github.mattpvaughn.chronicle.features.chooseserver

import io.github.mattpvaughn.chronicle.data.plex.model.Connection
import io.github.mattpvaughn.chronicle.data.plex.model.Device
import io.github.mattpvaughn.chronicle.data.plex.model.MediaContainer

data class ServerModel(val name: String, val connections: List<Connection>, val serverId: String) {
    fun getServerUri() : String {
        return connections.getOrNull(0)?.uri ?: ""
    }
}

fun MediaContainer.asServers(): List<ServerModel> {
    return devices?.filter { device -> device.provides.contains("server") }
        ?.map { device -> device.asServer() } ?: emptyList()
}

fun Device.asServer(): ServerModel {
    return ServerModel(this.name, this.connections, this.clientIdentifier)
}


