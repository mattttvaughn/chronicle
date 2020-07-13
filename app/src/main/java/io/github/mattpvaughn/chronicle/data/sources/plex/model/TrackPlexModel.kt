package io.github.mattpvaughn.chronicle.data.sources.plex.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack

/** A model for the "Media" element of a "Track" entity. Only requires a "Part" for our uses */
@JsonClass(generateAdapter = true)
data class Media(@Json(name = "Part") val part: List<Part> = emptyList())

/** A model for the "Part" element of a "Media" entity. Only need the key for our uses */
@JsonClass(generateAdapter = true)
data class Part(val key: String = "", val size: Long = 0)

fun List<PlexDirectory>?.asMediaItemTracks(): List<MediaItemTrack> {
    // Rewrite indices to reflect their order in audiobook, ignoring numbers passed from server
    return this?.map { song ->
        MediaItemTrack.fromPlexModel(networkTrack = song)
    } ?: emptyList()
}

fun List<MediaItemTrack>.getDuration(): Long {
    var acc = 0L
    this.forEach { track -> acc += track.duration }
    return acc
}
