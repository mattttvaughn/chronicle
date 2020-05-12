package io.github.mattpvaughn.chronicle.application

import android.app.DownloadManager
import android.app.DownloadManager.*
import android.support.v4.media.MediaMetadataCompat
import android.util.Log
import androidx.lifecycle.*
import io.github.mattpvaughn.chronicle.application.MainActivityViewModel.BottomSheetState.*
import io.github.mattpvaughn.chronicle.application.MainActivityViewModel.DownloadResult.Failure
import io.github.mattpvaughn.chronicle.application.MainActivityViewModel.DownloadResult.Success
import io.github.mattpvaughn.chronicle.data.local.IBookRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import io.github.mattpvaughn.chronicle.data.model.*
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME
import io.github.mattpvaughn.chronicle.data.plex.PlexConfig
import io.github.mattpvaughn.chronicle.features.player.MediaServiceConnection
import io.github.mattpvaughn.chronicle.features.player.id
import io.github.mattpvaughn.chronicle.features.player.isPlaying
import io.github.mattpvaughn.chronicle.features.player.title
import io.github.mattpvaughn.chronicle.util.Event
import io.github.mattpvaughn.chronicle.util.postEvent
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileFilter
import javax.inject.Inject


class MainActivityViewModel @Inject constructor(
    private val trackRepository: ITrackRepository,
    private val bookRepository: IBookRepository,
    private val mediaServiceConnection: MediaServiceConnection,
    private val prefsRepo: PrefsRepo,
    private val plexConfig: PlexConfig
) : ViewModel(), MainActivity.CurrentlyPlayingInterface {

    /** The status of the bottom sheet which contains "currently playing" info */
    enum class BottomSheetState {
        COLLAPSED,
        HIDDEN,
        EXPANDED
    }

    private var _isServerConnected = MutableLiveData(false)
    val isServerConnected: LiveData<Boolean>
        get() = _isServerConnected

    private val serverConnectionObserver = Observer<Boolean> { isConnectedToServer ->
        _isServerConnected.postValue(isConnectedToServer)
        if (isConnectedToServer) {
            refreshBookData()
        }
    }

    private var _currentlyPlayingLayoutState = MutableLiveData(HIDDEN)
    val currentlyPlayingLayoutState: LiveData<BottomSheetState>
        get() = _currentlyPlayingLayoutState

    private var _audiobook = MutableLiveData<Audiobook>(EMPTY_AUDIOBOOK)
    val audiobook: LiveData<Audiobook>
        get() = _audiobook

    private var _tracks = MutableLiveData<List<MediaItemTrack>>(emptyList())
    val tracks: LiveData<List<MediaItemTrack>>
        get() = _tracks

    private var _errorMessage = MutableLiveData<Event<String>>()
    val errorMessage: LiveData<Event<String>>
        get() = _errorMessage

    val currentTrackTitle = Transformations.map(tracks) { trackList ->
        Log.i(APP_NAME, "Current tracklist? $trackList")
        if (trackList.isNotEmpty()) {
            return@map trackList.getActiveTrack().title
        } else {
            return@map "No track playing"
        }
    }

    val isPlaying = Transformations.map(mediaServiceConnection.playbackState) {
        it.isPlaying
    }

    private val playbackObserver = Observer<MediaMetadataCompat> { metadata ->
        Log.i(APP_NAME, "Metadata: ${metadata.id}, ${metadata.title}")
        metadata.id?.let { trackId ->
            if (trackId.isNotEmpty()) {
                setAudiobook(trackId.toInt())
            }
        } ?: _currentlyPlayingLayoutState.postValue(HIDDEN)
    }

    init {
        mediaServiceConnection.nowPlaying.observeForever(playbackObserver)
        plexConfig.isConnected.observeForever(serverConnectionObserver)
        refreshTrackCacheStatus()
    }

    private fun setAudiobook(trackId: Int) {
        val currentAudiobookId = audiobook.value?.id ?: NO_AUDIOBOOK_FOUND_ID
        Log.i(APP_NAME, "Setting currently playing!")
        viewModelScope.launch {
            val bookId = trackRepository.getBookIdForTrack(trackId)
            // Only change the active audiobook if it differs from the one currently in metadata OR
            // there is none set in metadata
            if (currentAudiobookId == NO_AUDIOBOOK_FOUND_ID || bookId != currentAudiobookId) {
                _audiobook.postValue(bookRepository.getAudiobookAsync(bookId))
                _tracks.postValue(trackRepository.getTracksForAudiobookAsync(bookId))
                _currentlyPlayingLayoutState.postValue(COLLAPSED)
            }
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
            })?.map {
                MediaItemTrack.getTrackIdFromFileName(it.name)
            } ?: emptyList()

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
        val result = getTrackIdForDownload(downloadManager, downloadId)
        if (result is Success) {
            Log.i(APP_NAME, "Download completed: ${result.trackId}")
            val trackId = result.trackId
            viewModelScope.launch {
                trackRepository.updateCachedStatus(trackId, true)
                val bookId: Int = trackRepository.getBookIdForTrack(trackId)
                val tracks = trackRepository.getTracksForAudiobookAsync(bookId)
                // Set the book as cached only when all tracks in it have been cached
                val shouldBookBeCached =
                    tracks.filter { it.cached }.size == tracks.size && tracks.isNotEmpty()
                if (shouldBookBeCached) {
                    Log.i(APP_NAME, "Should be caching book with id $bookId")
                    bookRepository.updateCached(bookId, shouldBookBeCached)
                }
            }
        } else if (result is Failure) {
            Log.e(APP_NAME, result.reason)
            showUserMessage(result.reason)
        }
    }

    private sealed class DownloadResult {
        class Success(val trackId: Int) : DownloadResult()
        class Failure(val reason: String) : DownloadResult()
    }

    private fun getTrackIdForDownload(manager: DownloadManager, downloadId: Long): DownloadResult {
        val query = Query().apply { setFilterById(downloadId) }
        val cur = manager.query(query)
        if (!cur.moveToFirst()) {
            return Failure("No download found with id: $downloadId. Perhaps the download was canceled")
        }
        val statusColumnIndex = cur.getColumnIndex(COLUMN_STATUS)
        if (STATUS_SUCCESSFUL != cur.getInt(statusColumnIndex)) {
            val reasonColumnIndex = cur.getColumnIndex(COLUMN_REASON)
            val titleColumnIndex = cur.getColumnIndex(COLUMN_TITLE)
            return Failure(
                "Download failed for \"$titleColumnIndex\". " +
                        when (val errorReason = cur.getInt(reasonColumnIndex)) {
                            1008 -> "due to server issues (ERROR_CANNOT_RESUME). Please clear failed downloads from your Downloads app and try again"
                            else -> "Error code: ($errorReason)"
                        }
            )
        }
        val downloadedFilePath = cur.getString(cur.getColumnIndex(COLUMN_LOCAL_URI))

        /** Assume that the filename is also the key of the track */
        val trackName = File(downloadedFilePath.toString()).name
        if (!MediaItemTrack.cachedFilePattern.matches(trackName)) {
            throw IllegalStateException("Downloaded file does not match required pattern! Is this a duplicate download?")
        }
        return try {
            Success(MediaItemTrack.getTrackIdFromFileName(trackName))
        } catch (e: Exception) {
            Failure("Failed to get track id!")
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
        val transportControls = mediaServiceConnection.transportControls
        mediaServiceConnection.playbackState.value?.let { playbackState ->
            if (playbackState.isPlaying) {
                Log.i(APP_NAME, "Pausing!")
                transportControls.pause()
            } else {
                Log.i(APP_NAME, "Playing!")
                transportControls.play()
            }
        }
    }

    override fun onCleared() {
        mediaServiceConnection.nowPlaying.removeObserver(playbackObserver)
        plexConfig.isConnected.removeObserver(serverConnectionObserver)
        super.onCleared()
    }

    override fun setState(state: BottomSheetState) {
        _currentlyPlayingLayoutState.postValue(state)
    }

    private fun refreshBookData() {
        viewModelScope.launch {
            try {
                bookRepository.refreshData(trackRepository)
            } catch (e: Exception) {
                Log.e(APP_NAME, "Error loading book data! $e")
                e.printStackTrace()
            }
            try {
                trackRepository.refreshData()
            } catch (e: Throwable) {
                Log.e(APP_NAME, "Error loading track data! $e")
                e.printStackTrace()
            }
        }
    }


    fun showUserMessage(errorMessage: String) {
        _errorMessage.postEvent(errorMessage)
    }

    /** Minimize the currently playing modal/overlay if it is*/
    fun minimizeCurrentlyPlaying() {
        if (currentlyPlayingLayoutState.value == EXPANDED) {
            _currentlyPlayingLayoutState.postValue(COLLAPSED)
        }
    }
}

