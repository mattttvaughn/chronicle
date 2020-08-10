package io.github.mattpvaughn.chronicle.application

import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.session.PlaybackStateCompat.STATE_NONE
import android.support.v4.media.session.PlaybackStateCompat.STATE_STOPPED
import androidx.lifecycle.*
import io.github.mattpvaughn.chronicle.application.MainActivityViewModel.BottomSheetState.*
import io.github.mattpvaughn.chronicle.data.ICachedFileManager
import io.github.mattpvaughn.chronicle.data.local.IBookRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository
import io.github.mattpvaughn.chronicle.data.model.*
import io.github.mattpvaughn.chronicle.data.sources.SourceManager
import io.github.mattpvaughn.chronicle.features.player.MediaServiceConnection
import io.github.mattpvaughn.chronicle.features.player.id
import io.github.mattpvaughn.chronicle.features.player.isPlaying
import io.github.mattpvaughn.chronicle.util.DoubleLiveData
import io.github.mattpvaughn.chronicle.util.Event
import io.github.mattpvaughn.chronicle.util.mapAsync
import io.github.mattpvaughn.chronicle.util.postEvent
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject


class MainActivityViewModel(
    sourceManager: SourceManager,
    private val trackRepository: ITrackRepository,
    private val bookRepository: IBookRepository,
    private val mediaServiceConnection: MediaServiceConnection,
    private val cachedFileManager: ICachedFileManager
) : ViewModel(), MainActivity.CurrentlyPlayingInterface {

    @Suppress("UNCHECKED_CAST")
    class Factory @Inject constructor(
        private val sourceManager: SourceManager,
        private val trackRepository: ITrackRepository,
        private val bookRepository: IBookRepository,
        private val mediaServiceConnection: MediaServiceConnection,
        private val cachedFileManager: ICachedFileManager
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainActivityViewModel::class.java)) {
                return MainActivityViewModel(
                    sourceManager,
                    trackRepository,
                    bookRepository,
                    mediaServiceConnection,
                    cachedFileManager
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

    val isLoggedIn = sourceManager.isLoggedIn()

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
        viewModelScope.launch {
            cachedFileManager.refreshTrackDownloadedStatus()
        }
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

    fun handleDownloadedTrack(id: Long) {
        viewModelScope.launch {
            val downloadResult = cachedFileManager.handleDownloadedTrack(id)
            if (downloadResult.isFailure) {
                val downloadFailureMessage = downloadResult.exceptionOrNull()?.message
                if (downloadFailureMessage != null) {
                    _errorMessage.postEvent(downloadFailureMessage)
                }
            }
        }
    }
}

