package io.github.mattpvaughn.chronicle.data.plex.model

import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import org.simpleframework.xml.Attribute
import org.simpleframework.xml.Element
import org.simpleframework.xml.Root

/**
 * A model for the <Track/> entity, child of a [MediaContainer] returned by the Plex API. Used to
 * represent an audio track
 */
@Root(name = "Track", strict = false)
data class TrackPlexModel @JvmOverloads constructor(
    @field:Attribute
    var key: String = "",
    @field:Attribute
    var ratingKey: String = "",
    @field:Attribute
    var parentKey: String = "",
    @field:Attribute
    var title: String = "",
    @field:Attribute
    var parentTitle: String = "",
    @field:Attribute
    var grandparentTitle: String = "",
    @field:Attribute(required = false)
    var playQueueItemID: Long = 0L,
    @field:Attribute(required = false)
    var genre: String = "",
    @field:Attribute(required = false)
    var viewOffset: Long = 0L,
    @field:Attribute(required = false)
    var thumb: String? = null,
    @field:Attribute(required = false)
    var index: Int = 0,
    @field:Attribute(required = false)
    var duration: Long = 0L,
    @field:Attribute(required = false)
    var lastViewedAt: Long = 0L,
    @field:Attribute(required = false)
    var updatedAt: Long = 0L,
    @field:Element(required = false, name = "Media")
    var media: Media = Media()

)

/** A model for the "Media" element of a "Track" entity. Only requires a "Part" for our uses */
@Root(strict = false)
data class Media @JvmOverloads constructor(
    @field:Element(
        required = false,
        name = "Part"
    ) var part: Part = Part()
)

/** A model for the "Part" element of a "Media" entity. Only need the key for our uses */
@Root(strict = false)
data class Part @JvmOverloads constructor(
    @field:Attribute(
        required = false,
        name = "key"
    ) var key: String = "",
    @field:Attribute(required = false)
    var size: Long = 0
)

fun List<TrackPlexModel>?.asMediaItemTracks(): List<MediaItemTrack> {
    // Rewrite indices to reflect their order in audiobook, ignoring numbers passed from server
    return this?.mapIndexed { index, song ->
        MediaItemTrack.fromPlexModel(networkTrack = song, index = index)
    } ?: emptyList()
}

fun List<MediaItemTrack>.getDuration(): Long {
    var acc = 0L
    this.forEach { track -> acc += track.duration }
    return acc
}
