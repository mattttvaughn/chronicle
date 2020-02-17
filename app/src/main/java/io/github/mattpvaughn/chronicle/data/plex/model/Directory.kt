package io.github.mattpvaughn.chronicle.data.plex.model

import org.simpleframework.xml.Attribute
import org.simpleframework.xml.ElementList
import org.simpleframework.xml.Root

/**
 * Used to represent a <Directory/> element, typically contained by a [MediaContainer]. It
 * represents some type of container for audio tracks/chapters. This could be of type
 * [MediaType.ALBUM], [MediaType.ARTIST], or [MediaType.PERSON] within the context of audio.
 */
@Root(strict = false)
data class Directory @JvmOverloads constructor(
    @field:Attribute
    var title: String = "",
    @field:Attribute
    var key: String = "",
    @field:Attribute(required = false)
    var uuid: String = "",
    @field:Attribute(required = false)
    var parentTitle: String = "",
    @field:Attribute(required = false)
    var art: String = "",
    @field:Attribute(required = false)
    var ratingKey: String = "",
    @field:Attribute(required = false)
    var type: String = "",
    @field:Attribute(required = false)
    var thumb: String = "",
    @field:Attribute(required = false)
    var size: Int = 0,
    @field:Attribute(required = false)
    var parentRatingKey: Int = 0,
    @field:Attribute(required = false)
    var summary: String = "",
    @field:Attribute(required = false)
    var addedAt: Long = 0,
    @field:Attribute(required = false)
    var updatedAt: Long = 0,
    @field:Attribute(required = false)
    var viewedLeafCount: Long = 0,
    @field:Attribute(required = false)
    var leafCount: Long = 0,
    @field:Attribute(required = false)
    var lastViewedAt: Long = 0,
    @field:ElementList(inline = true, required = false, entry = "Genre")
    var genres: List<Genre> = ArrayList()
)

