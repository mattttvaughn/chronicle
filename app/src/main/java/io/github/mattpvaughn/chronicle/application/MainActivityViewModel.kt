package io.github.mattpvaughn.chronicle.application

import android.app.DownloadManager
import android.support.v4.media.MediaMetadataCompat
import android.util.Log
import androidx.lifecycle.*
import io.github.mattpvaughn.chronicle.application.MainActivityViewModel.BottomSheetState.*
import io.github.mattpvaughn.chronicle.data.local.IBookRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack.Companion.NO_TRACK_ID_FOUND
import io.github.mattpvaughn.chronicle.data.model.getActiveTrack
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME
import io.github.mattpvaughn.chronicle.data.plex.PlexPrefsRepo
import io.github.mattpvaughn.chronicle.features.player.MediaServiceConnection
import io.github.mattpvaughn.chronicle.features.player.id
import io.github.mattpvaughn.chronicle.features.player.isPlaying
import io.github.mattpvaughn.chronicle.features.settings.PrefsRepo
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileFilter
import javax.inject.Inject


class MainActivityViewModel @Inject constructor(
    private val trackRepository: ITrackRepository,
    private val bookRepository: IBookRepository,
    private val mediaServiceConnection: MediaServiceConnection,
    plexPrefsRepo: PlexPrefsRepo,
    private val prefsRepo: PrefsRepo
) : NetworkAwareViewModel(plexPrefsRepo), MainActivity.CurrentlyPlayingInterface {

    /** The status of the bottom sheet which contains "currently playing" info */
    enum class BottomSheetState {
        COLLAPSED,
        HIDDEN,
        EXPANDED
    }

    private var _currentlyPlayingLayoutState = MutableLiveData<BottomSheetState>(HIDDEN)
    val currentlyPlayingLayoutState: LiveData<BottomSheetState>
        get() = _currentlyPlayingLayoutState

    private var _audiobook = MutableLiveData<Audiobook?>(null)
    val audiobook: LiveData<Audiobook?>
        get() = _audiobook

    private var _tracks = MutableLiveData<List<MediaItemTrack>?>(null)
    val tracks: LiveData<List<MediaItemTrack>?>
        get() = _tracks

    private var _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String>
        get() = _errorMessage

    val thumb = Transformations.map(audiobook) { book ->
        book?.thumb ?: ""
    }

    val lastUpdated = Transformations.map(audiobook) { book ->
        book?.updatedAt ?: -1L
    }

    val bookTitle = Transformations.map(audiobook) { book ->
        book?.title ?: "No title"
    }

    val currentTrackTitle = Transformations.map(tracks) { trackList ->
        Log.i(APP_NAME, "Current tracklist? $trackList")
        trackList?.let {
            if (trackList.isNotEmpty()) {
                return@map trackList.getActiveTrack().title
            } else {
                return@map "No track playing"
            }
        } ?: return@map "No track playing"
    }

    val isPlaying = Transformations.map(mediaServiceConnection.playbackState) {
        it.isPlaying
    }

    private val playbackObserver = Observer<MediaMetadataCompat> { metadata ->
        metadata.id?.let { trackId ->
            if (trackId.isNotEmpty()) {
                setAudiobook(trackId.toInt())
            }
        } ?: _currentlyPlayingLayoutState.postValue(HIDDEN)
    }

    init {
        mediaServiceConnection.nowPlaying.observeForever(playbackObserver)
        refreshTrackCacheStatus()
    }

    val showConnectionError = Transformations.map(isConnected) {
        Log.i(APP_NAME, "Is connected? $it")
        !it
    }

    private fun setAudiobook(trackId: Int) {
        viewModelScope.launch {
            val bookId = trackRepository.getBookIdForTrack(trackId)
            _audiobook.postValue(bookRepository.getAudiobookAsync(bookId))
            _tracks.postValue(trackRepository.getTracksForAudiobookAsync(bookId))
            _currentlyPlayingLayoutState.postValue(COLLAPSED)
        }
    }

    /** Updates the DB to reflect whether cache files for tracks exist on disk */
    private fun refreshTrackCacheStatus() {
        viewModelScope.launch {
            // Keys of tracks which the DB reports as cached
            val reportedCachedKeys = trackRepository.getCachedTracks().map { it.id }

            // Keys of tracks which are cached on disk
            val actuallyCachedKeys = prefsRepo.cachedMediaDir.listFiles(FileFilter {
                MediaItemTrack.cachedFilePattern.matches(it.name)
            }).map {
                MediaItemTrack.getTrackIdFromFileName(it.name)
            }

            // Exists in DB but not in cache- remove from DB!
            reportedCachedKeys.filter { !actuallyCachedKeys.contains(it) }
                .forEach { trackRepository.updateCachedStatus(it, false) }

            // Exists in cache but not in DB- add to DB!
            actuallyCachedKeys.filter { !reportedCachedKeys.contains(it) }
                .forEach {
                    trackRepository.updateCachedStatus(it, true)
                }

            actuallyCachedKeys.map { trackRepository.getBookIdForTrack(it) }.distinct()
                .forEach { bookId: Int ->
                    val tracksCachedForBook =
                        trackRepository.getCachedTrackCountForBookAsync(bookId)
                    val tracksInBook = trackRepository.getTrackCountForBookAsync(bookId)
                    if (tracksInBook > 0 && tracksCachedForBook == tracksInBook) {
                        bookRepository.updateCached(bookId, true)
                    }
                }
        }
    }

    fun handleDownloadedTrack(downloadManager: DownloadManager, downloadId: Long) {
        val trackId = getTrackIdForDownload(downloadManager, downloadId)
        Log.i(APP_NAME, "Track id is: $trackId")
        viewModelScope.launch {
            trackRepository.updateCachedStatus(trackId, true)
            val bookId: Int = trackRepository.getBookIdForTrack(trackId)
            val tracks = trackRepository.getTracksForAudiobookAsync(bookId)
            val shouldBookBeCached =
                tracks.filter { it.cached }.size == tracks.size && tracks.isNotEmpty()
            if (shouldBookBeCached) {
                Log.i(APP_NAME, "Should be caching book with id $bookId")
                bookRepository.updateCached(bookId, shouldBookBeCached)
            }
        }
    }

    private fun getTrackIdForDownload(downloadManager: DownloadManager, downloadId: Long): Int {
        val query = DownloadManager.Query().apply { setFilterById(downloadId) }
        val cur = downloadManager.query(query)
        if (!cur.moveToFirst()) {
            Log.i(
                APP_NAME,
                "No download found with id: $downloadId. (A download queue may have been canceled after starting)"
            )
            return NO_TRACK_ID_FOUND
        }
        val statusColumnIndex = cur.getColumnIndex(DownloadManager.COLUMN_STATUS)
        if (DownloadManager.STATUS_SUCCESSFUL != cur.getInt(statusColumnIndex)) {
            Log.e(APP_NAME, "Download was not successful for download with id: $downloadId")
            return NO_TRACK_ID_FOUND
        }
        val downloadedFilePath = cur.getString(cur.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
        /** Assume that the filename is also the key of the track */
        val trackName = File(downloadedFilePath.toString()).name
        if (!MediaItemTrack.cachedFilePattern.matches(trackName)) {
            throw IllegalStateException("Downloaded file does not match required pattern! Is this a duplicate download?")
        }
        Log.i(APP_NAME, "Download completed: $trackName")
        return try {
            MediaItemTrack.getTrackIdFromFileName(trackName)
        } catch (e: Exception) {
            Log.e(APP_NAME, "Failed to get track id!")
            NO_TRACK_ID_FOUND
        } finally {
            cur.close()
        }
    }

    /**
     * React to clicks on the "currently playing" modal, which is shown at the bottom of the
     * R.layout.activity_main view when media is active (can be playing or paused)
     */
    fun onCurrentlyPlayingClicked() {
        when (currentlyPlayingLayoutState.value) {
            COLLAPSED -> _currentlyPlayingLayoutState.postValue(EXPANDED)
            EXPANDED -> _currentlyPlayingLayoutState.postValue(COLLAPSED)
            HIDDEN -> throw IllegalStateException("Cannot click on hidden sheet!")
        }
    }

    fun pausePlayButtonClicked() {
        mediaServiceConnection.playbackState.value?.let { playbackState ->
            if (playbackState.isPlaying) {
                mediaServiceConnection.transportControls?.pause()
            } else {
                mediaServiceConnection.transportControls?.play()
            }
        }
    }

    override fun onCleared() {
        mediaServiceConnection.nowPlaying.removeObserver(playbackObserver)
        super.onCleared()
    }

    override fun setState(state: BottomSheetState) {
        _currentlyPlayingLayoutState.postValue(state)
    }

    fun showUserMessage(errorMessage: String) {
        _errorMessage.postValue(errorMessage)
    }

    /** Minimize the currently playing modal/overlay if it is*/
    fun minimizeCurrentlyPlaying() {
        if (currentlyPlayingLayoutState.value == EXPANDED) {
            _currentlyPlayingLayoutState.postValue(COLLAPSED)
        }
    }

}

