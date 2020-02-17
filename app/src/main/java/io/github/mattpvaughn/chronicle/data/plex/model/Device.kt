package io.github.mattpvaughn.chronicle.data.plex.model

import org.simpleframework.xml.Attribute
import org.simpleframework.xml.ElementList
import org.simpleframework.xml.Root

/**
 * A <Device/> type object from the Plex API. Can represent a Plex server, player, or remote, as
 * designated by the [provides] field
 */
@Root(strict = false)
data class Device @JvmOverloads constructor(
    @field:Attribute(required = false)
    var name: String = "",
    @field:Attribute
    var provides: String = "",
    @field:Attribute(required = false)
    var accessToken: String = "",
    @field:ElementList(inline = true, entry = "Connection")
    var connections: List<Connection> = ArrayList(),
    @field:Attribute(required = false)
    var clientIdentifier: String = ""
)

@Root(strict = false)
data class Connection @JvmOverloads constructor(
    @field:Attribute
    var uri: String = "",
    @field:Attribute
    var local: Int = 0 // Local = 1, remote = 0
)
