package io.github.mattpvaughn.chronicle.data.sources.plex.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.github.mattpvaughn.chronicle.data.model.PlexLibrary

/**
 * Used to represent a <Directory/> element, typically contained by a [PlexMediaContainer]. It
 * represents some type of container for audio tracks/chapters. This could be of type
 * [MediaType.ALBUM], [MediaType.ARTIST], or [MediaType.PERSON] within the context of audio.
 */
@JsonClass(generateAdapter = true)
data class PlexDirectory(
    val key: String = "",
    val title: String = "",
    val titleSort: String = "",
    val ratingKey: String = "",
    val parentRatingKey: Int = 0,
    val parentTitle: String = "",
    val type: String = "",
    val grandparentTitle: String = "",
    val thumb: String = "",
    val size: Int = 0,
    val summary: String = "",
    val parentYear: Int = 0,
    val year: Int = 0,
    val addedAt: Long = 0,
    val updatedAt: Long = 0,
    val viewedLeafCount: Long = 0,
    val leafCount: Long = 0,
    val lastViewedAt: Long = 0,
    val viewCount: Long = 0,
    val plexGenres: List<PlexGenre> = emptyList(),
    @Json(name = "Chapter")
    val plexChapters: List<PlexChapter> = emptyList(),
    val duration: Long = 0L,
    val index: Int = 0,
    val parentIndex: Int = 1,
    @Json(name = "Media")
    val media: List<Media> = emptyList(),
    val viewOffset: Long = 0L
)

fun PlexDirectory.asLibrary(): PlexLibrary {
    return PlexLibrary(
        name = title,
        type = MediaType.TYPES.find { mediaType -> mediaType.typeString == this.type }
            ?: MediaType.ARTIST,
        id = key)
}

