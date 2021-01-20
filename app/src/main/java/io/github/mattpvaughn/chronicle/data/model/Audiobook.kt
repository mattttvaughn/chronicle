package io.github.mattpvaughn.chronicle.data.model

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import io.github.mattpvaughn.chronicle.data.*
import io.github.mattpvaughn.chronicle.data.sources.MediaSource
import io.github.mattpvaughn.chronicle.data.sources.MediaSource.Companion.NO_SOURCE_FOUND
import io.github.mattpvaughn.chronicle.data.sources.SourceManager
import io.github.mattpvaughn.chronicle.data.sources.plex.model.PlexDirectory
import io.github.mattpvaughn.chronicle.features.player.*

@TypeConverters(ChapterListConverter::class)
@Entity
data class Audiobook constructor(
    @PrimaryKey
    val id: Int,
    /** Long representing a unique [MediaSource] in [SourceManager] */
    val source: Long,
    val title: String = "",
    val titleSort: String = "",
    val author: String = "",
    val thumb: String = "",
    val parentId: Int = -1,
    val genre: String = "",
    val summary: String = "",
    val addedAt: Long = 0L,
    /** last Unix timestamp that some metadata was changed in server */
    val updatedAt: Long = 0L,
    /** last Unix timestamp that the book was listened to */
    val lastViewedAt: Long = 0L,
    /** duration of the entire audiobook in milliseconds */
    val duration: Long = 0L,
    /** Whether the book is cached by [ICachedFileManager]*/
    val isCached: Boolean = false,
    /** The current progress into the audiobook in milliseconds */
    val progress: Long = 0L,
    val favorited: Boolean = false,
    /** The number of time's individual tracks have been completed */
    val viewedLeafCount: Long = 0L,
    /** The number of tracks in the book */
    val leafCount: Long = 0L,
    /** Chapter metadata corresponding to m4b chapter metadata in the m4b files */
    val chapters: List<Chapter> = emptyList()
) {

    companion object {
        fun from(dir: PlexDirectory, id: Long): Audiobook {
            return Audiobook(
                id = dir.ratingKey.toInt(),
                source = id,
                title = dir.title,
                titleSort = dir.titleSort.takeIf { it.isNotEmpty() } ?: dir.title,
                author = dir.parentTitle,
                thumb = dir.thumb,
                parentId = dir.parentRatingKey,
                genre = dir.plexGenres.joinToString(separator = ", "),
                summary = dir.summary,
                addedAt = dir.addedAt,
                updatedAt = dir.updatedAt,
                lastViewedAt = dir.lastViewedAt,
                viewedLeafCount = dir.viewedLeafCount,
                leafCount = dir.leafCount
            )
        }

        /**
         * Merges updated local fields with a network copy of the book. Respects network metadata
         * as the authoritative source of truth with the follow exceptions:
         *
         * Retains the following local fields if the local copy is more up to date: [lastViewedAt].
         * This is because even if the network copy is more up to date, retaining the most recent
         * [lastViewedAt] from the local copy is preferred.
         *
         * Always retain fields from local copy: [duration], [isCached], [favorited], [chapters],
         * [source]. We retain [chapters] and [duration] because they can be calculated only when
         * all child [MediaItemTrack]'s of the Audiobook are loaded. We retain [duration], [source],
         * and [isCached] because they are explicitly local values, they do not even exist on the
         * server
         *
         * TODO: Alternate [merge]s depending on [MediaSource] may be necessary
         */
        fun merge(network: Audiobook, local: Audiobook): Audiobook {
            return if (network.lastViewedAt > local.lastViewedAt) {
                network.copy(
                    duration = local.duration,
                    isCached = local.isCached,
                    favorited = local.favorited,
                    chapters = local.chapters,
                    source = local.source
                )
            } else {
                network.copy(
                    duration = local.duration,
                    source = local.source,
                    isCached = local.isCached,
                    lastViewedAt = local.lastViewedAt,
                    favorited = local.favorited,
                    chapters = local.chapters
                )
            }
        }

        const val SORT_KEY_TITLE = "title"
        const val SORT_KEY_AUTHOR = "author"
        const val SORT_KEY_GENRE = "title"
        const val SORT_KEY_RELEASE_DATE = "release_date"
        const val SORT_KEY_DURATION = "duration"
        const val SORT_KEY_RATING = "rating"
        const val SORT_KEY_CRITIC_RATING = "critic_rating"
        const val SORT_KEY_DATE_ADDED = "date_added"
        const val SORT_KEY_DATE_PLAYED = "date_played"
        const val SORT_KEY_PLAYS = "plays"

        val SORT_KEYS = listOf(
            SORT_KEY_TITLE,
            SORT_KEY_AUTHOR,
            SORT_KEY_GENRE,
            SORT_KEY_RELEASE_DATE,
            SORT_KEY_RATING,
            SORT_KEY_CRITIC_RATING,
            SORT_KEY_DATE_ADDED,
            SORT_KEY_DATE_PLAYED,
            SORT_KEY_PLAYS,
            SORT_KEY_DURATION
        )
    }
}

fun Audiobook.toAlbumMediaMetadata(): MediaMetadataCompat {
    val metadataBuilder = MediaMetadataCompat.Builder()
    metadataBuilder.id = this.id.toString()
    metadataBuilder.title = this.title
    metadataBuilder.displayTitle = this.title
    metadataBuilder.albumArtUri = this.thumb
    metadataBuilder.album = this.title
    metadataBuilder.artist = this.author
    metadataBuilder.genre = this.genre
    return metadataBuilder.build()
}

/**
 * Converts an audiobook to a [MediaBrowserCompat.MediaItem] for use in
 * [androidx.media.MediaBrowserServiceCompat.onSearch] and
 * [androidx.media.MediaBrowserServiceCompat.onLoadChildren], and respective clients
 */
fun Audiobook.toMediaItem(mediaSource: MediaSource?): MediaBrowserCompat.MediaItem {
    val mediaDescription = MediaDescriptionCompat.Builder()
    mediaDescription.setTitle(title)
    mediaDescription.setMediaId(id.toString())
    mediaDescription.setSubtitle(author)
    if (mediaSource != null) {
        mediaDescription.setIconUri(mediaSource.makeThumbUri(this.thumb))
    }
    val extras = Bundle()
    extras.putBoolean(EXTRA_IS_DOWNLOADED, isCached)
    extras.putInt(
        EXTRA_PLAY_COMPLETION_STATE, if (progress == 0L) {
            STATUS_NOT_PLAYED
        } else {
            STATUS_PARTIALLY_PLAYED
        }
    )
    mediaDescription.setExtras(extras)

    return MediaBrowserCompat.MediaItem(mediaDescription.build(), FLAG_PLAYABLE)
}

const val NO_AUDIOBOOK_FOUND_ID = -22321
const val NO_AUDIOBOOK_FOUND_TITLE = "No audiobook found"
val EMPTY_AUDIOBOOK = Audiobook(NO_AUDIOBOOK_FOUND_ID, NO_SOURCE_FOUND, NO_AUDIOBOOK_FOUND_TITLE)
