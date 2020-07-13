package io.github.mattpvaughn.chronicle.application

import android.app.DownloadManager
import android.app.DownloadManager.*
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.session.PlaybackStateCompat.STATE_NONE
import android.support.v4.media.session.PlaybackStateCompat.STATE_STOPPED
import androidx.lifecycle.*
import io.github.mattpvaughn.chronicle.application.MainActivityViewModel.BottomSheetState.*
import io.github.mattpvaughn.chronicle.application.MainActivityViewModel.DownloadResult.Failure
import io.github.mattpvaughn.chronicle.application.MainActivityViewModel.DownloadResult.Success
import io.github.mattpvaughn.chronicle.data.local.IBookRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import io.github.mattpvaughn.chronicle.data.model.*
import io.github.mattpvaughn.chronicle.data.sources.plex.IPlexLoginRepo
import io.github.mattpvaughn.chronicle.data.sources.plex.IPlexLoginRepo.LoginState.LOGGED_IN_FULLY
import io.github.mattpvaughn.chronicle.features.player.MediaServiceConnection
import io.github.mattpvaughn.chronicle.features.player.id
import io.github.mattpvaughn.chronicle.features.player.isPlaying
import io.github.mattpvaughn.chronicle.util.DoubleLiveData
import io.github.mattpvaughn.chronicle.util.Event
import io.github.mattpvaughn.chronicle.util.mapAsync
import io.github.mattpvaughn.chronicle.util.postEvent
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileFilter
import javax.inject.Inject


class MainActivityViewModel(
    loginRepo: IPlexLoginRepo,
    private val trackRepository: ITrackRepository,
    private val bookRepository: IBookRepository,
    private val mediaServiceConnection: MediaServiceConnection,
    private val prefsRepo: PrefsRepo
) : ViewModel(), MainActivity.CurrentlyPlayingInterface {

    @Suppress("UNCHECKED_CAST")
    class Factory @Inject constructor(
        private val loginRepo: IPlexLoginRepo,
        private val trackRepository: ITrackRepository,
        private val bookRepository: IBookRepository,
        private val mediaServiceConnection: MediaServiceConnection,
        private val prefsRepo: PrefsRepo
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainActivityViewModel::class.java)) {
                return MainActivityViewModel(
                    loginRepo,
                    trackRepository,
                    bookRepository,
                    mediaServiceConnection,
                    prefsRepo
                ) as T
            } else {
                throw IllegalArgumentException("Cannot instantiate $modelClass from MainActivityViewModel.Factory")
            }
        }
    }

    /** The status of the bottom sheet which contains "currently playing" info */
    enum class BottomSheetState {
        COLLAPSED,
        HIDDEN,
        EXPANDED
    }

    val isLoggedIn = Transformations.map(loginRepo.loginState) { it == LOGGED_IN_FULLY }

    private var _currentlyPlayingLayoutState = MutableLiveData(HIDDEN)
    val currentlyPlayingLayoutState: LiveData<BottomSheetState>
        get() = _currentlyPlayingLayoutState

    private var audiobookId = MutableLiveData(NO_AUDIOBOOK_FOUND_ID)

    val audiobook = mapAsync(audiobookId, viewModelScope) { id ->
        bookRepository.getAudiobookAsync(id) ?: EMPTY_AUDIOBOOK
    }

    private var tracks = Transformations.switchMap(audiobookId) { id ->
        if (id != NO_AUDIOBOOK_FOUND_ID) {
            trackRepository.getTracksForAudiobook(id)
        } else {
            MutableLiveData(emptyList())
        }
    }

    private var _errorMessage = MutableLiveData<Event<String>>()
    val errorMessage: LiveData<Event<String>>
        get() = _errorMessage

    val currentTrackTitle = DoubleLiveData(tracks, audiobook) { _tracks, _audiobook ->
        val chapters: List<Chapter>? = _audiobook?.chapters
        if (!chapters.isNullOrEmpty() && !_tracks.isNullOrEmpty()) {
            val activeTrack = _tracks.getActiveTrack()
            val currentTrackProgress: Long = activeTrack.progress
            val currentTrackOffset: Long = _tracks.getTrackStartTime(activeTrack)
            return@DoubleLiveData chapters.getChapterAt(currentTrackOffset + currentTrackProgress).title
        }
        if (!_tracks.isNullOrEmpty()) {
            val activeTrack = _tracks.getActiveTrack()
            return@DoubleLiveData activeTrack.title
        } else {
            return@DoubleLiveData "No track playing"
        }
    }

    val isPlaying = Transformations.map(mediaServiceConnection.playbackState) {
        it.isPlaying
    }

    private val metadataObserver = Observer<MediaMetadataCompat> { metadata ->
        metadata.id?.let { trackId ->
            if (trackId.isNotEmpty()) {
                viewModelScope.launch(Injector.get().unhandledExceptionHandler()) {
                    setAudiobook(trackId.toInt())
                }
            }
        } ?: _currentlyPlayingLayoutState.postValue(HIDDEN)
    }

    private val playbackObserver = Observer<PlaybackStateCompat> { state ->
        Timber.i("Observing playback: $state")
        when (state.state) {
            STATE_STOPPED, STATE_NONE -> setBottomSheetState(HIDDEN)
            else -> {
                if (currentlyPlayingLayoutState.value == HIDDEN) {
                    setBottomSheetState(COLLAPSED)
                }
            }
        }
    }

    init {
        mediaServiceConnection.nowPlaying.observeForever(metadataObserver)
        mediaServiceConnection.playbackState.observeForever(playbackObserver)
        refreshTrackCacheStatus()
    }

    private suspend fun setAudiobook(trackId: Int) {
        val previousAudiobookId = audiobook.value?.id ?: NO_AUDIOBOOK_FOUND_ID
        viewModelScope.launch(Injector.get().unhandledExceptionHandler()) {
            val bookId = trackRepository.getBookIdForTrack(trackId)
            // Only change the active audiobook if it differs from the one currently in metadata
            if (previousAudiobookId != bookId && bookId != NO_AUDIOBOOK_FOUND_ID) {
                audiobookId.postValue(bookId)
                if (_currentlyPlayingLayoutState.value == HIDDEN) {
                    _currentlyPlayingLayoutState.postValue(COLLAPSED)
                }
            }
        }
    }

    /** Updates the DB to reflect whether cache files for tracks exist on disk */
    private fun refreshTrackCacheStatus() {
        viewModelScope.launch(Injector.get().unhandledExceptionHandler()) {
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
            Timber.i("Download completed: ${result.trackId}")
            val trackId = result.trackId
            viewModelScope.launch(Injector.get().unhandledExceptionHandler()) {
                trackRepository.updateCachedStatus(trackId, true)
                val bookId: Int = trackRepository.getBookIdForTrack(trackId)
                val tracks = trackRepository.getTracksForAudiobookAsync(bookId)
                // Set the book as cached only when all tracks in it have been cached
                val shouldBookBeCached =
                    tracks.filter { it.cached }.size == tracks.size && tracks.isNotEmpty()
                if (shouldBookBeCached) {
                    Timber.i("Should be caching book with id $bookId")
                    bookRepository.updateCached(bookId, shouldBookBeCached)
                }
            }
        } else if (result is Failure) {
            Timber.e(result.reason)
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
        } catch (e: Throwable) {
            Failure("Failed to get track id: ${e.message}")
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
        if (mediaServiceConnection.isConnected.value != true) {
            mediaServiceConnection.connect(this::pausePlay)
        } else {
            pausePlay()
        }
    }

    private fun pausePlay() {
        // Require [mediaServiceConnection] is connected
        check(mediaServiceConnection.isConnected.value == true)
        val transportControls = mediaServiceConnection.transportControls
        mediaServiceConnection.playbackState.value?.let { playbackState ->
            if (playbackState.isPlaying) {
                Timber.i("Pausing!")
                transportControls?.pause()
            } else {
                Timber.i("Playing!")
                transportControls?.play()
            }
        }
    }

    override fun onCleared() {
        mediaServiceConnection.nowPlaying.removeObserver(metadataObserver)
        mediaServiceConnection.playbackState.removeObserver(playbackObserver)
        super.onCleared()
    }

    override fun setBottomSheetState(state: BottomSheetState) {
        _currentlyPlayingLayoutState.postValue(state)
    }


    fun showUserMessage(errorMessage: String) {
        _errorMessage.postEvent(errorMessage)
    }

    /** Minimize the currently playing modal/overlay if it is expanded */
    fun minimizeCurrentlyPlaying() {
        if (currentlyPlayingLayoutState.value == EXPANDED) {
            _currentlyPlayingLayoutState.postValue(COLLAPSED)
        }
    }

    /** Maximize the currently playing modal/overlay if it is visible, but not expanded yet */
    fun maximizeCurrentlyPlaying() {
        if (currentlyPlayingLayoutState.value != EXPANDED) {
            _currentlyPlayingLayoutState.postValue(EXPANDED)
        }
    }

    fun onCurrentlyPlayingHandleDragged() {
        if (currentlyPlayingLayoutState.value == COLLAPSED) {
            _currentlyPlayingLayoutState.postValue(EXPANDED)
        }
    }
}

