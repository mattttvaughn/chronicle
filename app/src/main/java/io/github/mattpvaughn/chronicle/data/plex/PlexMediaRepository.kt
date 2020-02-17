package io.github.mattpvaughn.chronicle.data.plex

import android.support.v4.media.MediaMetadataCompat
import androidx.lifecycle.LiveData
import io.github.mattpvaughn.chronicle.data.local.IBookRepository
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.model.toAlbumMediaMetadata
import io.github.mattpvaughn.chronicle.features.player.AbstractMediaSource

class PlexMediaRepository(private val bookRepository: IBookRepository) : AbstractMediaSource() {

    private val bookIndex = 0
    private lateinit var books: LiveData<List<Audiobook>>

    override suspend fun load() {
        books = bookRepository.getAllBooks()
    }

    override fun whenReady(performAction: (Boolean) -> Unit): Boolean {
        performAction.invoke(true)
        return true
    }

    // Needs to iterate over books and tracks
    override fun iterator(): Iterator<MediaMetadataCompat> {
        return object : Iterator<MediaMetadataCompat> {
            override fun hasNext(): Boolean {
                return bookIndex < (books.value?.size ?: 0) - 1
            }

            override fun next(): MediaMetadataCompat {
                requireNotNull(books.value)
                return books.value!![bookIndex].toAlbumMediaMetadata()
            }

        }
    }
}

