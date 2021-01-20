package io.github.mattpvaughn.chronicle.data.sources.plex.model

import com.squareup.moshi.JsonClass

/**
 * A <Device/> type object from the Plex API. Can represent a Plex server, player, or remote, as
 * designated by the [provides] field
 */
@JsonClass(generateAdapter = true)
data class PlexServer(
    val name: String = "",
    val provides: String = "",
    val connections: List<Connection> = emptyList(),
    val clientIdentifier: String = "",
    val accessToken: String? = "",
    val owned: Boolean = true // assume owned server as this is probably more common
)

@JsonClass(generateAdapter = true)
data class Connection(val uri: String = "", val local: Boolean = false)
