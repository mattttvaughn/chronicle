package io.github.mattpvaughn.chronicle.data.model

import android.support.v4.media.MediaMetadataCompat
import android.util.Log
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME
import io.github.mattpvaughn.chronicle.data.plex.PlexRequestSingleton
import io.github.mattpvaughn.chronicle.data.plex.model.TrackPlexModel
import io.github.mattpvaughn.chronicle.features.player.*
import java.io.File

/**
 * A model for an audio track (i.e. a song)
 */
@Entity
data class MediaItemTrack(
    @PrimaryKey
    val id: Int = -1,
    val parentKey: Int = -1,
    val title: String = "",
    val playQueueItemID: Long = -1,
    val thumb: String? = null,
    val index: Int = 0,
    /** The duration of the track in milliseconds */
    val duration: Long = 0L,
    val media: String = "",
    val album: String = "",
    val artist: String = "",
    val genre: String = "",
    val cached: Boolean = false,
    val artwork: String? = "",
    val progress: Long = 0L,
    val lastViewedAt: Long = 0L,
    val updatedAt: Long = 0L,
    val size: Long = 0L
) {
    companion object {
        fun from(metadata: MediaMetadataCompat): MediaItemTrack {
            return MediaItemTrack(
                id = metadata.id?.toInt() ?: -1,
                title = metadata.title ?: "",
                playQueueItemID = metadata.trackNumber,
                thumb = metadata.artUri.toString(),
                index = metadata.trackNumber.toInt(),
                duration = metadata.duration,
                album = metadata.album ?: "",
                artist = metadata.artist ?: "",
                genre = metadata.genre ?: "",
                artwork = metadata.artUri.toString()
            )

        }

        const val NO_TRACK_ID_FOUND = -111

        val cachedFilePattern = Regex("\\d*\\..+")
        fun getTrackIdFromFileName(fileName: String): Int {
            return fileName.substringBefore('.').toInt()
        }

        /**
         * Merges updated local fields with a network copy of the book. Prefers network metadata,
         * but retains the following local fields if the local copy is more up to date: [lastViewedAt], [progress]
         *
         * Always retains [cached] field from local copy
         */
        fun merge(networkTrack: MediaItemTrack, localTrack: MediaItemTrack): MediaItemTrack {
            return if (networkTrack.lastViewedAt > localTrack.lastViewedAt) {
                Log.i(APP_NAME, "Integrating network track: $networkTrack")
                networkTrack.copy(cached = localTrack.cached)
            } else {
                networkTrack.copy(
                    cached = localTrack.cached,
                    lastViewedAt = localTrack.lastViewedAt,
                    progress = localTrack.progress
                )
            }
        }

        /** Create a [MediaItemTrack] from a Plex model and an index */
        fun fromPlexModel(networkTrack: TrackPlexModel, index: Int): MediaItemTrack {
            return MediaItemTrack(
                id = networkTrack.ratingKey.toInt(),
                parentKey = networkTrack.parentKey.replace(PARENT_KEY_PREFIX, "").toInt(),
                title = networkTrack.title,
                artist = networkTrack.grandparentTitle,
                thumb = networkTrack.thumb,
                index = index + 1, // b/c humans don't like 0-indexing!
                duration = networkTrack.duration,
                progress = networkTrack.viewOffset,
                media = networkTrack.media.part.key,
                playQueueItemID = networkTrack.playQueueItemID,
                album = networkTrack.parentTitle,
                lastViewedAt = networkTrack.lastViewedAt,
                updatedAt = networkTrack.updatedAt,
                size = networkTrack.media.part.size
            )
        }

        const val PARENT_KEY_PREFIX = "/library/metadata/"
    }

    /** Count track as finished if within 5 seconds from the end */
    fun isFinished(): Boolean {
        return progress > duration - 5
    }

    /** The name of the track when it is written to the file system */
    fun getCachedFileName(): String {
        return "$id.${File(media).extension}"
    }

    fun getTrackSource(): String {
        return if (cached) {
            getCachedFileName()
        } else {
            media
        }
    }

    /** A string representing the index but padded to [length] characters with zeroes */
    fun paddedIndex(length: Int): String {
        return index.toString().padStart(length, '0')
    }
}


/**
 * Returns the timestamp corresponding to the start of [track] with respect to the entire playlist
 */
fun List<MediaItemTrack>.getTrackStartTime(track: MediaItemTrack): Long {
    if (isEmpty()) {
        return 0
    }
    val previousTracks = this.subList(0, indexOf(track))
    return previousTracks.map { it.duration }.sum()
}

/**
 * Return the progress of the current track plus the duration of all previous tracks
 */
fun List<MediaItemTrack>.getProgress(): Long {
    if (isEmpty()) {
        return 0
    }
    val currentTrackProgress = getActiveTrack().progress
    val previousTracksDuration = getTrackStartTime(getActiveTrack())
    return currentTrackProgress + previousTracksDuration
}

/**
 * Find the next song in the [List] which has not been completed. Returns the first element
 */
fun List<MediaItemTrack>.getActiveTrack(): MediaItemTrack {
    check(this.isNotEmpty()) { "Cannot get active track of empty list!" }
    return maxBy { it.lastViewedAt } ?: get(0)
}

/**
 * Converts a [MediaItemTrack] to a [MediaMetadataCompat]. Requires a [file] to be passed in so
 * that a uri can be generated for cached media
 */
fun MediaItemTrack.toMediaMetadata(file: File): MediaMetadataCompat {
    val metadataBuilder = MediaMetadataCompat.Builder()
    metadataBuilder.id = this.id.toString()
    metadataBuilder.title = this.title
    metadataBuilder.trackNumber = this.playQueueItemID
    metadataBuilder.albumArtUri = this.thumb
    metadataBuilder.mediaUri = if (cached) {
        File(file, getCachedFileName()).absolutePath
    } else {
        PlexRequestSingleton.toServerString(getTrackSource())
    }
    metadataBuilder.trackNumber = this.index.toLong()
    metadataBuilder.duration = this.duration
    metadataBuilder.album = this.album
    metadataBuilder.artist = this.artist
    metadataBuilder.genre = this.genre
    return metadataBuilder.build()
}
