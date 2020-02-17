package io.github.mattpvaughn.chronicle.data.model

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.util.Log
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.mattpvaughn.chronicle.data.plex.*
import io.github.mattpvaughn.chronicle.data.plex.model.Directory
import io.github.mattpvaughn.chronicle.features.player.*

@Entity
data class Audiobook(
    @PrimaryKey
    val id: Int,
    val title: String = "",
    val author: String = "",
    val thumb: String = "",
    val parentId: Int = -1,
    val genre: String = "",
    val summary: String = "",
    val addedAt: Long = 0L,
    val updatedAt: Long = 0L,
    val lastViewedAt: Long = 0L,
    val duration: Long = 0L,
    val isCached: Boolean = false,
    val favorited: Boolean = false,
    val viewedLeafCount: Long = 0L,
    val leafCount: Long = 0L
) {
    companion object {
        fun from(directory: Directory): Audiobook {
            return Audiobook(
                id = directory.ratingKey.toInt(),
                title = directory.title,
                author = directory.parentTitle,
                thumb = directory.thumb,
                parentId = directory.parentRatingKey,
                genre = directory.genres.joinToString(separator = ", "),
                summary = directory.summary,
                addedAt = directory.addedAt,
                updatedAt = directory.updatedAt,
                lastViewedAt = directory.lastViewedAt,
                viewedLeafCount = directory.viewedLeafCount,
                leafCount = directory.leafCount
            )
        }

        /**
         * Merges updated local fields with a network copy of the book. Prefers network metadata,
         * but retains the following local fields if the local copy is more up to date: [lastViewedAt].
         *
         * Always retain fields from local copy: [duration], [isCached], [favorited]
         */
        fun merge(networkBook: Audiobook, localBook: Audiobook): Audiobook {
            return if (networkBook.lastViewedAt > localBook.lastViewedAt) {
                Log.i(APP_NAME, "Integrating remote book for: ${networkBook.title}")
                networkBook.copy(
                    duration = localBook.duration,
                    isCached = localBook.isCached,
                    favorited = localBook.favorited
                )
            } else {
                networkBook.copy(
                    duration = localBook.duration,
                    isCached = localBook.isCached,
                    lastViewedAt = localBook.lastViewedAt,
                    favorited = localBook.favorited
                )
            }
        }
    }
}

fun Audiobook.toAlbumMediaMetadata(): MediaMetadataCompat {
    val metadataBuilder = MediaMetadataCompat.Builder()
    metadataBuilder.id = this.id.toString()
    metadataBuilder.title = this.title
    metadataBuilder.albumArtUri = this.thumb
    metadataBuilder.album = this.title
    metadataBuilder.artist = this.author
    metadataBuilder.genre = this.genre
    return metadataBuilder.build()
}


fun Audiobook.toMediaItem(): MediaBrowserCompat.MediaItem {
    val mediaDescription = MediaDescriptionCompat.Builder()
    mediaDescription.setTitle(title)
    mediaDescription.setMediaId(id.toString())
    mediaDescription.setSubtitle(author)
    val extras = Bundle()
    extras.putBoolean(CONTENT_STYLE_SUPPORTED, true)
    extras.putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST_ITEM_HINT_VALUE)
    extras.putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_LIST_ITEM_HINT_VALUE)
    extras.putBoolean(EXTRA_IS_DOWNLOADED, isCached)
    mediaDescription.setExtras(extras)
    return MediaBrowserCompat.MediaItem(mediaDescription.build(), FLAG_PLAYABLE)
}

val EMPTY_AUDIOBOOK = Audiobook(-1, "No title")
