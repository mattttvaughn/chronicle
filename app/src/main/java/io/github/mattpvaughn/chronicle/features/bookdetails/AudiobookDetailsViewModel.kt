package io.github.mattpvaughn.chronicle.features.bookdetails

import android.media.session.MediaController
import android.media.session.PlaybackState.*
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.format.DateUtils
import androidx.lifecycle.*
import com.github.michaelbull.result.Ok
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.data.local.IBookRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository.Companion.TRACK_NOT_FOUND
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import io.github.mattpvaughn.chronicle.data.model.*
import io.github.mattpvaughn.chronicle.data.sources.plex.ICachedFileManager
import io.github.mattpvaughn.chronicle.data.sources.plex.ICachedFileManager.CacheStatus.*
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexConfig
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexMediaService
import io.github.mattpvaughn.chronicle.features.player.*
import io.github.mattpvaughn.chronicle.features.player.MediaPlayerService.Companion.ACTIVE_TRACK
import io.github.mattpvaughn.chronicle.features.player.MediaPlayerService.Companion.KEY_SEEK_TO_TRACK_WITH_ID
import io.github.mattpvaughn.chronicle.features.player.MediaPlayerService.Companion.KEY_START_TIME_OFFSET
import io.github.mattpvaughn.chronicle.features.player.MediaPlayerService.Companion.PLEX_STATE_STOPPED
import io.github.mattpvaughn.chronicle.features.player.MediaPlayerService.Companion.USE_TRACK_ID
import io.github.mattpvaughn.chronicle.util.DoubleLiveData
import io.github.mattpvaughn.chronicle.util.Event
import io.github.mattpvaughn.chronicle.util.mapAsync
import io.github.mattpvaughn.chronicle.util.postEvent
import io.github.mattpvaughn.chronicle.views.BottomSheetChooser.*
import io.github.mattpvaughn.chronicle.views.BottomSheetChooser.BottomChooserState.Companion.EMPTY_BOTTOM_CHOOSER
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

class AudiobookDetailsViewModel(
    private val bookRepository: IBookRepository,
    private val trackRepository: ITrackRepository,
    private val cachedFileManager: ICachedFileManager,
    // Just a skeleton of an audiobook. Only guaranteed to contain a correct [Audiobook.id]
    private val inputAudiobook: Audiobook,
    private val mediaServiceConnection: MediaServiceConnection,
    private val progressUpdater: ProgressUpdater,
    private val plexConfig: PlexConfig,
    private val prefsRepo: PrefsRepo,
    private val plexMediaService: PlexMediaService
) : ViewModel() {

    @Suppress("UNCHECKED_CAST")
    class Factory @Inject constructor(
        private val bookRepository: IBookRepository,
        private val trackRepository: ITrackRepository,
        private val cachedFileManager: ICachedFileManager,
        private val mediaServiceConnection: MediaServiceConnection,
        private val progressUpdater: ProgressUpdater,
        private val plexConfig: PlexConfig,
        private val prefsRepo: PrefsRepo,
        private val plexMediaService: PlexMediaService
    ) : ViewModelProvider.Factory {
        lateinit var inputAudiobook: Audiobook
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            check(this::inputAudiobook.isInitialized) { "Input audiobook not provided!" }
            if (modelClass.isAssignableFrom(AudiobookDetailsViewModel::class.java)) {
                return AudiobookDetailsViewModel(
                    bookRepository,
                    trackRepository,
                    cachedFileManager,
                    inputAudiobook,
                    mediaServiceConnection,
                    progressUpdater,
                    plexConfig,
                    prefsRepo,
                    plexMediaService
                ) as T
            } else {
                throw IllegalStateException("Wrong class provided to ${this.javaClass.name}")
            }
        }
    }

    val audiobook: LiveData<Audiobook?> = bookRepository.getAudiobook(inputAudiobook.id)
    val tracks = trackRepository.getTracksForAudiobook(inputAudiobook.id)

    // Used to cache tracks.asChapterList when tracks changes
    private val tracksAsChaptersCache: LiveData<List<Chapter>> = mapAsync(tracks, viewModelScope) {
        it.asChapterList()
    }

    val chapters: DoubleLiveData<Audiobook?, List<Chapter>, List<Chapter>> =
        DoubleLiveData(
            audiobook,
            tracksAsChaptersCache
        ) { _audiobook: Audiobook?, _tracksAsChapters: List<Chapter>? ->
            if (_audiobook?.chapters?.isNotEmpty() == true) {
                _audiobook.chapters
            } else {
                _tracksAsChapters ?: emptyList()
            }
        }

    private var _messageForUser = MutableLiveData<Event<String>>()
    val messageForUser: LiveData<Event<String>>
        get() = _messageForUser

    // Default cache status if [tracks] hasn't loaded, or an override if currently caching
    private var _manualCacheStatus =
        MutableLiveData(if (inputAudiobook.isCached) CACHED else NOT_CACHED)

    /**
     * Cache status of the current audiobook. Reflects the cache status of [tracks] if they've
     * been loaded, otherwise default to [_manualCacheStatus].
     *
     * Special case: if [_manualCacheStatus] is [CACHING], use that value
     */
    val cacheStatus = DoubleLiveData(_manualCacheStatus, audiobook) { default, _audiobook ->
        if (_audiobook?.isCached == true) {
            return@DoubleLiveData CACHED
        }
        if (default == CACHING) {
            return@DoubleLiveData CACHING
        }
        return@DoubleLiveData when (_audiobook?.isCached) {
            false -> NOT_CACHED
            else -> default
        }
    }

    val cacheIconTint: LiveData<Int> = Transformations.map(cacheStatus) { status ->
        return@map when (status) {
            CACHING -> R.color.icon // Doesn't matter, we should a spinner over it
            NOT_CACHED -> R.color.icon
            CACHED -> R.color.iconActive
            else -> throw NoWhenBranchMatchedException("Unknown cache status!")
        }
    }

    val cacheIconDrawable: LiveData<Int> = Transformations.map(cacheStatus) { status ->
        return@map when (status) {
            CACHING -> R.drawable.ic_cloud_download_white // Doesn't matter, we show a spinner over it
            NOT_CACHED -> R.drawable.ic_cloud_download_white
            CACHED -> R.drawable.ic_cloud_done_white
            else -> throw NoWhenBranchMatchedException("Unknown cache status!")
        }
    }

    /**
     * The title of the book currently playing, as agreed upon by [MediaServiceConnection.nowPlaying]
     * and [audiobook].
     *
     * If the titles do not match, or either is null, return [NO_AUDIOBOOK_FOUND_TITLE]
     */
    private val currentlyPlayingBookTitle =
        DoubleLiveData<MediaMetadataCompat, Audiobook?, String>(
            mediaServiceConnection.nowPlaying,
            audiobook
        ) { currentlyPlaying, currentBook ->
            return@DoubleLiveData if (currentlyPlaying?.displayTitle != null && currentlyPlaying.displayTitle == currentBook?.title) {
                currentlyPlaying.displayTitle ?: NO_AUDIOBOOK_FOUND_TITLE
            } else {
                NO_AUDIOBOOK_FOUND_TITLE
            }
        }

    /** Whether the book in the current view is also the same on in the [MediaController] */
    private val isBookInViewActive =
        DoubleLiveData<String, PlaybackStateCompat, Boolean>(
            currentlyPlayingBookTitle,
            mediaServiceConnection.playbackState
        ) { currTitle, currState ->
            return@DoubleLiveData currentlyPlayingBookTitle.value != NO_AUDIOBOOK_FOUND_TITLE
                    && currState?.state != STATE_NONE
                    && currState?.state != PlaybackStateCompat.STATE_ERROR
        }

    /** Whether the book in the current view is playing */
    val isBookInViewPlaying =
        DoubleLiveData<Boolean, PlaybackStateCompat, Boolean>(
            isBookInViewActive,
            mediaServiceConnection.playbackState
        ) { isBookActive, currState ->
            return@DoubleLiveData isBookActive ?: false && currState?.isPlaying ?: false
        }

    val progressString = Transformations.map(tracks) {
        return@map DateUtils.formatElapsedTime(StringBuilder(), it.getProgress() / 1000)
    }

    val durationString = Transformations.map(audiobook) {
        return@map DateUtils.formatElapsedTime(StringBuilder(), (it?.duration ?: 0L) / 1000)
    }

    private var _isLoadingTracks = MutableLiveData(false)
    val isLoadingTracks: LiveData<Boolean>
        get() = _isLoadingTracks


    private var _bottomChooserState = MutableLiveData(EMPTY_BOTTOM_CHOOSER)
    val bottomChooserState: LiveData<BottomChooserState>
        get() = _bottomChooserState

    // The maximum number of lines to shown in the info section
    private val lineCountSummaryMinimized = 5
    private val lineCountSummaryMaximized = Int.MAX_VALUE
    private var _summaryLinesShown = MutableLiveData(lineCountSummaryMinimized)
    val summaryLinesShown: LiveData<Int>
        get() = _summaryLinesShown

    val isAudioLoading = Transformations.map(mediaServiceConnection.playbackState) { state ->
        if (state.state == PlaybackStateCompat.STATE_ERROR) {
            Timber.i("Playback state: ${state.stateName}, (${state.errorMessage})")
        } else {
            Timber.i("Playback state: ${state.stateName}")
        }
        state.state == STATE_BUFFERING || state.state == STATE_CONNECTING
    }

    val showSummary = Transformations.map(audiobook) { book ->
        book?.summary?.isNotEmpty() ?: false
    }

    val isExpanded = Transformations.map(summaryLinesShown) { lines ->
        return@map lines == lineCountSummaryMaximized
    }

    val serverConnection = Transformations.map(plexConfig.connectionState) { it }

    fun onToggleSummaryView() {
        _summaryLinesShown.value =
            if (_summaryLinesShown.value == lineCountSummaryMinimized) lineCountSummaryMaximized else lineCountSummaryMinimized
    }

    @InternalCoroutinesApi
    fun connectToServer() {
        viewModelScope.launch(Dispatchers.IO) {
            plexConfig.connectToServer(plexMediaService)
        }
    }

    private val networkObserver = Observer<Boolean> { isConnected ->
        if (isConnected) {
            refreshChapterInfo(inputAudiobook.id)
        }
    }

    init {
        plexConfig.isConnected.observeForever(networkObserver)
    }

    /**
     * Refresh the tracks for the current audiobook. Mostly important because we want to refresh
     * the progress in the audiobook is there's a new
     */
    private fun refreshChapterInfo(bookId: Int) {
        Timber.i("Refreshing tracks!")
        viewModelScope.launch {
            try {
                // If we're just updating underlying track list, and there are already tracks/chapters
                // loaded, don't replace chapter view with loading view
                _isLoadingTracks.value =
                    tracks.value?.isNullOrEmpty() == true && chapters.value?.isNullOrEmpty() == true
                val trackRequest = trackRepository.loadTracksForAudiobook(bookId)
                if (trackRequest is Ok) {
                    val audiobook = bookRepository.getAudiobookAsync(bookId)
                    audiobook?.let {
                        bookRepository.loadChapterData(audiobook, trackRequest.value)
                    }
                }
                _isLoadingTracks.value = false
            } catch (e: Throwable) {
                Timber.e("Failed to load tracks for audiobook ${bookId}: $e")
                _isLoadingTracks.value = false
            }
        }
    }

    private fun cancelCaching() {
        // Cancel queued downloads
        cachedFileManager.cancelCaching()
        _manualCacheStatus.postValue(NOT_CACHED)
    }

    fun onCacheButtonClick() {
        if (!prefsRepo.isPremium) {
            showUserMessage("Premium is required for offline access!")
            return
        }
        when (cacheStatus.value) {
            NOT_CACHED -> {
                Timber.i("Caching tracks for \"${audiobook.value?.title}\"")
                if (plexConfig.isConnected.value != true) {
                    showUserMessage("Unable to cache audiobook \"${audiobook.value?.title}\", not connected to server")
                    _manualCacheStatus.postValue(NOT_CACHED)
                } else {
                    _manualCacheStatus.postValue(CACHING)
                    cachedFileManager.downloadTracks(tracks.value ?: emptyList())
                }
            }
            CACHED -> promptUserToUncache()
            CACHING -> cancelCaching()
            else -> throw NoWhenBranchMatchedException("Unknown cache status. Don't know how to proceed")
        }
    }

    private fun showUserMessage(message: String) {
        _messageForUser.postEvent(message)
    }

    private fun promptUserToUncache() {
        showOptionsMenu(
            title = FormattableString.from(R.string.delete_cache_files_prompt),
            options = listOf(FormattableString.yes, FormattableString.no),
            listener = object : BottomChooserItemListener() {
                override fun onItemClicked(formattableString: FormattableString) {
                    when (formattableString) {
                        FormattableString.yes -> uncacheFiles()
                        FormattableString.no -> { /* Do nothing */
                        }
                        else -> throw NoWhenBranchMatchedException("Unknown option selected!")
                    }
                    hideBottomSheet()
                }
            }
        )
    }

    private fun uncacheFiles() {
        viewModelScope.launch {
            val result =
                cachedFileManager.deleteCachedBook(tracks.value ?: emptyList())
            _manualCacheStatus.postValue(NOT_CACHED)
            if (result.isFailure) {
                val messageString = result.exceptionOrNull()?.message ?: return@launch
                _messageForUser.postEvent(messageString)
            }
        }
    }

    fun pausePlayButtonClicked() {
        if (plexConfig.isConnected.value != true && audiobook.value?.isCached == false) {
            _messageForUser.postEvent("Cannot play media- not connected to any server!")
            return
        }

        val pausePlayAction =
            { pausePlay(inputAudiobook.id.toString(), startTimeOffset = ACTIVE_TRACK) }
        if (mediaServiceConnection.isConnected.value != true) {
            mediaServiceConnection.connect(pausePlayAction)
        } else {
            pausePlayAction()
        }
    }

    /**
     * Play or pause the audiobook with id [bookId] depending whether playback is active (as
     * determined by [isBookInViewPlaying]
     *
     * Assume that [mediaServiceConnection] has connected
     *
     * Play behavior: start/resume playback from [startTimeOffset] milliseconds from the start of
     * the book. [startTimeOffset] == [ACTIVE_TRACK] indicates that playback should be resumed from
     * the most recent playback location
     */
    private fun pausePlay(
        bookId: String,
        startTimeOffset: Long = ACTIVE_TRACK,
        trackId: Int = TRACK_NOT_FOUND,
        forcePlayFromMediaId: Boolean = false
    ) {
        updateProgressForChangingBook()

        val transportControls = mediaServiceConnection.transportControls ?: return

        val extras = Bundle().apply {
            putLong(KEY_START_TIME_OFFSET, startTimeOffset)
            putInt(KEY_SEEK_TO_TRACK_WITH_ID, trackId)
        }
        Timber.i(
            "is this book playing? ${isBookInViewPlaying.value}, this this book active? ${isBookInViewActive.value}"
        )
        when {
            forcePlayFromMediaId -> {
                transportControls.playFromMediaId(bookId, extras)
            }
            isBookInViewPlaying.value == true -> {
                transportControls.pause()
            }
            isBookInViewActive.value == true -> {
                transportControls.play()
            }
            else -> {
                transportControls.playFromMediaId(bookId, extras)
            }
        }
    }

    /**
     * Check if there is an active audiobook which are about to be replaced by a different
     * audiobook and if so, make a network request to inform the server that playback has ended
     */
    private fun updateProgressForChangingBook() {
        val currentlyPlayingTrackId = mediaServiceConnection.nowPlaying.value?.id
        val isChangingBooks = if (currentlyPlayingTrackId.isNullOrEmpty()) {
            false
        } else {
            Timber.i("Currently playing is $currentlyPlayingTrackId")
            tracks.value?.let { trackList ->
                trackList.any { it.id == currentlyPlayingTrackId.toInt() }
            } ?: false
        }

        if (isChangingBooks) {
            if (!currentlyPlayingTrackId.isNullOrEmpty()) {
                mediaServiceConnection.playbackState.value?.let { state ->
                    progressUpdater.updateProgress(
                        currentlyPlayingTrackId.toInt(),
                        PLEX_STATE_STOPPED,
                        state.currentPlayBackPosition,
                        true
                    )
                }
            }
        }
    }

    /** Jumps to a chapter starting [offset] milliseconds into the audiobook */
    fun jumpToChapter(
        offset: Long = USE_TRACK_ID,
        trackId: Int = TRACK_NOT_FOUND,
        hasUserConfirmation: Boolean = false
    ) {
        if (!hasUserConfirmation) {
            showOptionsMenu(
                title = FormattableString.from(R.string.warning_jump_to_chapter_will_clear_progress),
                options = listOf(FormattableString.yes, FormattableString.no),
                listener = object : BottomChooserItemListener() {
                    override fun onItemClicked(formattableString: FormattableString) {
                        when (formattableString) {
                            FormattableString.yes -> jumpToChapter(offset, trackId, true)
                            FormattableString.no -> Unit
                            else -> throw NoWhenBranchMatchedException()
                        }
                        hideBottomSheet()
                    }
                })
            return
        }

        val jumpToChapterAction = {
            audiobook.value?.let { book ->
                pausePlay(book.id.toString(), offset, trackId, forcePlayFromMediaId = true)
            }
        }
        if (mediaServiceConnection.isConnected.value != true) {
            mediaServiceConnection.connect(onConnected = jumpToChapterAction)
        } else {
            jumpToChapterAction()
        }
    }


    private fun hideBottomSheet() {
        Timber.i("Hiding bottom sheet?")
        _bottomChooserState.postValue(
            _bottomChooserState.value?.copy(shouldShow = false) ?: EMPTY_BOTTOM_CHOOSER
        )
    }

    private fun showOptionsMenu(
        title: FormattableString,
        options: List<FormattableString>,
        listener: BottomChooserListener
    ) {
        _bottomChooserState.postValue(
            BottomChooserState(
                title = title,
                options = options,
                listener = listener,
                shouldShow = true
            )
        )
    }

    override fun onCleared() {
        plexConfig.isConnected.removeObserver(networkObserver)
        super.onCleared()
    }
}
