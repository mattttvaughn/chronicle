package io.github.mattpvaughn.chronicle.data.sources.plex.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.model.Collection
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack

@JsonClass(generateAdapter = true)
data class PlexMediaContainerWrapper(@Json(name = "MediaContainer") val plexMediaContainer: PlexMediaContainer)

@JsonClass(generateAdapter = true)
data class PlexMediaContainer(
    val playQueueSelectedItemID: Long = -1,
    @Json(name = "Directory")
    val plexDirectories: List<PlexDirectory> = emptyList(),
    @Json(name = "Metadata")
    val metadata: List<PlexDirectory> = emptyList(),
    val mediaProvider: MediaProvider? = null,
    val devices: List<PlexServer> = emptyList(),
    val size: Long = 0,
    val totalSize: Long = 0,
    val offset: Long = 0
)

@JsonClass(generateAdapter = true)
data class PlexGenre(val tag: String = "")

fun PlexMediaContainer.asAudiobooks(): List<Audiobook> {
    return metadata.map { Audiobook.from(it) }
}

fun PlexMediaContainer.asTrackList(): List<MediaItemTrack> {
    return metadata.asMediaItemTracks()
}

fun PlexMediaContainer.asCollections(): List<Collection> {
    return metadata.map { Collection.from(it) }
}
