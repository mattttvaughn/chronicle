package io.github.mattpvaughn.chronicle.data.plex.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack

@JsonClass(generateAdapter = true)
data class MediaContainerWrapper(@Json(name = "MediaContainer") val mediaContainer: MediaContainer)

@JsonClass(generateAdapter = true)
data class MediaContainer(
    val playQueueSelectedItemID: Long = -1,
    @Json(name = "Directory")
    val directories: List<Directory> = mutableListOf(),
    @Json(name = "Metadata")
    val metadata: List<Directory> = mutableListOf(),
    val devices: List<Server> = mutableListOf(),
    val size: Long = 0
)

@JsonClass(generateAdapter = true)
data class Genre(val tag: String = "")

fun MediaContainer.asAudiobooks(): List<Audiobook> {
    return metadata.map { Audiobook.from(it) }
}

fun MediaContainer.asTrackList(): List<MediaItemTrack> {
    return metadata.asMediaItemTracks()
}
