package io.github.mattpvaughn.chronicle.data.local

import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.data.model.getProgress
import io.github.mattpvaughn.chronicle.data.sources.plex.model.getDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibrarySyncRepository @Inject constructor(
    private val bookRepository: BookRepository,
    private val trackRepository: TrackRepository,
    private val collectionsRepository: CollectionsRepository
) {

    private var repoJob = Job()
    private val repoScope = CoroutineScope(repoJob + Dispatchers.IO)

    private var _isRefreshing = MutableLiveData<Boolean>()
    val isRefreshing: LiveData<Boolean>
        get() = _isRefreshing

    fun refreshLibrary() {
        repoScope.launch {
            try {
                _isRefreshing.postValue(true)
                bookRepository.refreshDataPaginated()
                trackRepository.refreshDataPaginated()
            } catch (e: Throwable) {
                val msg = "Failed to refresh data: ${e.message}"
                Toast.makeText(Injector.get().applicationContext(), msg, LENGTH_LONG).show()
            } finally {
                _isRefreshing.postValue(false)
            }

            // TODO: Loading all data into memory :O
            val audiobooks = bookRepository.getAllBooksAsync()
            val tracks = trackRepository.getAllTracksAsync()
            audiobooks.forEach { book ->
                // TODO: O(n^2) so could be bad for big libs, grouping by tracks first would be O(n)

                // Not necessarily in the right order, but it doesn't matter for updateTrackData
                val tracksInAudiobook = tracks.filter { it.parentKey == book.id }
                bookRepository.updateTrackData(
                    bookId = book.id,
                    bookProgress = tracksInAudiobook.getProgress(),
                    bookDuration = tracksInAudiobook.getDuration(),
                    trackCount = tracksInAudiobook.size
                )
            }

            collectionsRepository.refreshCollectionsPaginated()
        }
    }

}
