package io.github.mattpvaughn.chronicle.data.local

import io.github.mattpvaughn.chronicle.data.model.getProgress
import io.github.mattpvaughn.chronicle.data.sources.plex.model.getDuration
import timber.log.Timber

class DataManager {

    companion object {
        suspend fun refreshData(
            bookRepository: IBookRepository,
            trackRepository: ITrackRepository
        ): String? {
            val result = try {
                val bookResults = bookRepository.refreshData()
                val trackResults = trackRepository.refreshData()
                val allResults = bookResults.union(trackResults)
                val failures =
                    allResults.filter { it.isFailure }.map { it.exceptionOrNull()?.message ?: "" }
                return if (failures.isNotEmpty()) {
                    "Failed to load (${failures.size}/${allResults.size}): $failures"
                } else {
                    null
                }
            } catch (e: Throwable) {
                e.message
            }

            val audiobooks = bookRepository.getAllBooksAsync()
            val tracks = trackRepository.getAllTracksAsync()
            audiobooks.forEach { book ->
                val tracksInAudiobook = tracks.filter { it.parentServerId == book.id }
                Timber.i("Book progress: ${tracksInAudiobook.getProgress()}")
                bookRepository.updateTrackData(
                    bookId = book.id,
                    bookProgress = tracksInAudiobook.getProgress(),
                    bookDuration = tracksInAudiobook.getDuration(),
                    trackCount = tracksInAudiobook.size
                )
            }
            return result
        }
    }
}