package io.github.mattpvaughn.chronicle.data.sources

import io.github.mattpvaughn.chronicle.data.local.BookRepository
import io.github.mattpvaughn.chronicle.data.local.TrackRepository
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import javax.inject.Inject

class SourceManager @Inject constructor(
    private val bookRepository: BookRepository,
    private val trackRepository: TrackRepository
) {
    private val sources = mutableListOf<MediaSource>()

    fun getSources(): List<MediaSource> {
        return sources.toList()
    }

    /** Adds a [MediaSource] from [sources] then refreshes data */
    suspend fun addSource(mediaSource: MediaSource) {
        sources.add(mediaSource)
        refreshBooks()
    }

    /** Removes a [MediaSource] from [sources] then refreshes data if the removal succeeded */
    suspend fun removeSource(mediaSource: MediaSource) {
        val removed = sources.remove(mediaSource)
        if (removed) {
            refreshBooks()
        }
    }

    /**
     * Calls [MediaSource.fetchAudiobooks] and [MediaSource.fetchTracks] respectively, for all
     * [MediaSource]s in [sources]. Updates the local [bookRepository] to reflect the [Audiobook]s
     * and [MediaItemTrack]s returned
     *
     * Failures to fetch data by a [MediaSource] will result in local data being retained, rather
     * than being deleted
     */
    suspend fun refreshBooks() {
        val books = sources.map { source ->
            source.fetchAudiobooks().component1() ?: emptyList()
        }
        val tracks = sources.map { source ->
            source.fetchTracks().component1() ?: emptyList()
        }

    }
}