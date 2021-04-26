package io.github.mattpvaughn.chronicle.features.bookdetails

import android.media.session.MediaController
import android.media.session.PlaybackState.*
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.format.DateUtils
import androidx.lifecycle.*
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.data.ConnectionState
import io.github.mattpvaughn.chronicle.data.ICachedFileManager
import io.github.mattpvaughn.chronicle.data.ICachedFileManager.CacheStatus.*
import io.github.mattpvaughn.chronicle.data.local.IBookRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository.Companion.TRACK_NOT_FOUND
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import io.github.mattpvaughn.chronicle.data.model.*
import io.github.mattpvaughn.chronicle.data.sources.HttpMediaSource
import io.github.mattpvaughn.chronicle.data.sources.MediaSource
import io.github.mattpvaughn.chronicle.data.sources.plex.model.getDuration
import io.github.mattpvaughn.chronicle.features.currentlyplaying.CurrentlyPlaying
import io.github.mattpvaughn.chronicle.features.player.*
import io.github.mattpvaughn.chronicle.features.player.MediaPlayerService.Companion.ACTIVE_TRACK
import io.github.mattpvaughn.chronicle.features.player.MediaPlayerService.Companion.KEY_SEEK_TO_TRACK_WITH_ID
import io.github.mattpvaughn.chronicle.features.player.MediaPlayerService.Companion.KEY_START_TIME_TRACK_OFFSET
import io.github.mattpvaughn.chronicle.features.player.MediaPlayerService.Companion.PLEX_STATE_STOPPED
import io.github.mattpvaughn.chronicle.features.player.MediaPlayerService.Companion.USE_SAVED_TRACK_PROGRESS
import io.github.mattpvaughn.chronicle.util.DoubleLiveData
import io.github.mattpvaughn.chronicle.util.Event
import io.github.mattpvaughn.chronicle.util.mapAsync
import io.github.mattpvaughn.chronicle.util.postEvent
import io.github.mattpvaughn.chronicle.views.BottomSheetChooser.*
import io.github.mattpvaughn.chronicle.views.BottomSheetChooser.BottomChooserState.Companion.EMPTY_BOTTOM_CHOOSER
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import timber.log.Timber
import javax.inject.Inject

@ExperimentalCoroutinesApi
class AudiobookDetailsViewModel(
    private val bookRepository: IBookRepository,
    private val trackRepository: ITrackRepository,
    private val cachedFileManager: ICachedFileManager,
    // Just the skeleton of an audiobook. Only guaranteed to contain a correct [Audiobook.id], [Audiobook.title]
    private val inputAudiobook: Audiobook,
    private val mediaServiceConnection: MediaServiceConnection,
    private val progressUpdater: ProgressUpdater,
    private val mediaSource: MediaSource,
    private val prefsRepo: PrefsRepo,
    currentlyPlaying: CurrentlyPlaying
) : ViewModel() {

    @Suppress("UNCHECKED_CAST")
    class Factory @Inject constructor(
        private val bookRepository: IBookRepository,
        private val trackRepository: ITrackRepository,
        private val cachedFileManager: ICachedFileManager,
        private val mediaServiceConnection: MediaServiceConnection,
        private val progressUpdater: ProgressUpdater,
        private val prefsRepo: PrefsRepo,
        private val currentlyPlaying: CurrentlyPlaying
    ) : ViewModelProvider.Factory {

        lateinit var inputAudiobook: Audiobook
        lateinit var source: MediaSource

        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            check(this::inputAudiobook.isInitialized) { "Input audiobook not provided!" }
            check(this::source.isInitialized) { "Source not provided" }
            if (modelClass.isAssignableFrom(AudiobookDetailsViewModel::class.java)) {
                return AudiobookDetailsViewModel(
                    bookRepository,
                    trackRepository,
                    cachedFileManager,
                    inputAudiobook,
                    mediaServiceConnection,
                    progressUpdater,
                    source,
                    prefsRepo,
                    currentlyPlaying,
                ) as T
            } else {
                throw IllegalStateException("Wrong class provided to ${this.javaClass.name}")
            }
        }
    }

    val sourceId = inputAudiobook.source
    val audiobook: LiveData<Audiobook?> = bookRepository.getAudiobook(inputAudiobook.id)
    val tracks = trackRepository.getTracksForAudiobook(sourceId, inputAudiobook.serverId)

    // Used to cache tracks.asChapterList when tracks changes
    private val tracksAsChaptersCache: LiveData<List<Chapter>> = mapAsync(tracks, viewModelScope) {
        it.asChapterList()
    }

    val chapters: DoubleLiveData<Audiobook?, List<Chapter>, List<Chapter>> =
        DoubleLiveData(
            audiobook,
            tracksAsChaptersCache
        ) { _audiobook: Audiobook?, _tracksAsChapters: List<Chapter>? ->
            Timber.i("Chapter data updated! ")
            if (_audiobook?.chapters.isNotEmpty() == true) {
                _audiobook.chapters
            } else {
                Timber.i("Chapters: $_tracksAsChapters")
                _tracksAsChapters ?: emptyList()
            }
        }

    private var _messageForUser = MutableLiveData<Event<FormattableString>>()
    val messageForUser: LiveData<Event<FormattableString>>
        get() = _messageForUser

    /**
     * Cache status of the current audiobook. Reflects the cache status of [tracks] if they've
     * been loaded, otherwise default to [_manualCacheStatus].
     */
    val cacheStatus = DoubleLiveData(
        cachedFileManager.activeBookDownloads,
        audiobook
    ) { activeDownloadIDs: Set<Int>?, _audiobook: Audiobook? ->
        Timber.i("Active downloads: $activeDownloadIDs")
        return@DoubleLiveData when {
            _audiobook?.isCached == true -> CACHED
            inputAudiobook.id in (activeDownloadIDs ?: emptySet()) -> CACHING
            else -> NOT_CACHED
        }
    }

    val cacheIconTint = Transformations.map(cacheStatus) { status ->
        return@map when (status) {
            CACHING -> R.color.icon // Doesn't matter, we show a spinner over it
            NOT_CACHED -> R.color.icon
            CACHED -> R.color.iconActive
            null -> R.color.icon
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
        ) { _, currState ->
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

    val progressString = Transformations.map(tracks) { tracks: List<MediaItemTrack> ->
        if (tracks.isNullOrEmpty()) {
            return@map "0:00/0:00"
        }
        val progressStr = DateUtils.formatElapsedTime(StringBuilder(), tracks.getProgress() / 1000L)
        val durationStr = DateUtils.formatElapsedTime(StringBuilder(), tracks.getDuration() / 1000L)
        return@map "$progressStr/$durationStr"
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

    fun onToggleSummaryView() {
        _summaryLinesShown.value =
            if (_summaryLinesShown.value == lineCountSummaryMinimized) lineCountSummaryMaximized else lineCountSummaryMinimized
    }

    @InternalCoroutinesApi
    fun connectToServer() {
        viewModelScope.launch(Dispatchers.IO) {
            if (mediaSource is HttpMediaSource) {
                mediaSource.connectToRemote()
            }
        }
    }

    val connectionState = if (mediaSource is HttpMediaSource) {
        mediaSource.connectionState
    } else {
        MutableLiveData(ConnectionState.CONNECTED)
    }

    private val networkObserver = Observer<ConnectionState> { connection ->
        if (connection == ConnectionState.CONNECTED) {
            if (mediaSource is HttpMediaSource) {
                loadBookDetails(mediaSource, inputAudiobook.id)
            }
        }
    }

    private val cachedChapter = DoubleLiveData(
        chapters,
        tracks
    ) { _chapters: List<Chapter>?, _tracks: List<MediaItemTrack>? ->
        Timber.i("Cached chapters: $_chapters")
        Timber.i("Cached progress: ${_tracks?.getProgress()}")

        if (_tracks != null && _chapters != null) {
            var offsetRemaining = _tracks.getProgress()
            var currChapter: Chapter? = null
            for (chapter in _chapters) {
                if (offsetRemaining < chapter.endTimeOffset) {
                    currChapter = chapter
                    break
                }
                offsetRemaining -= (chapter.endTimeOffset - chapter.startTimeOffset)
            }
            currChapter ?: EMPTY_CHAPTER
        } else {
            EMPTY_CHAPTER
        }
    }.asFlow()

    val activeChapter = currentlyPlaying.chapter.combine(cachedChapter)
    { activeChapter: Chapter, cachedChapter: Chapter ->
        Timber.i("Cached: $cachedChapter, active: $activeChapter")
        if (activeChapter != EMPTY_CHAPTER && activeChapter.trackId == cachedChapter.trackId) {
            activeChapter
        } else {
            cachedChapter
        }
    }.asLiveData(viewModelScope.coroutineContext)


    init {
        if (mediaSource is HttpMediaSource) {
            mediaSource.connectionState.observeForever(networkObserver)
        }
    }

    /**
     * Refresh details for the current audiobook. Mostly important because we want to refresh the
     * progress in the audiobook is there has been new playback
     */
    private fun loadBookDetails(mediaSource: MediaSource, bookId: Int) {
        Timber.i("Refreshing tracks!")
        viewModelScope.launch {
            try {
                // If we're just updating underlying track list, and there are already tracks/chapters
                // loaded, don't replace chapter view with loading view.
                //
                // Delay for 50ms to ensure chapters have loaded from db
                delay(50)
                if (mediaSource !is HttpMediaSource) {
                    return@launch
                }
                val noExistingChapters = chapters.value.isNullOrEmpty()
                _isLoadingTracks.value = noExistingChapters
                val trackRequest = trackRepository.loadTracksForAudiobook(bookId, mediaSource)
                val requestData = trackRequest.getOrNull()
                if (requestData != null) {
                    val audiobook = bookRepository.getAudiobookAsync(bookId)
                    audiobook?.let {
                        trackRepository.syncTracksInBook(audiobook.id, mediaSource)
                        bookRepository.syncAudiobook(
                            source = mediaSource,
                            audiobook = audiobook,
                            tracks = requestData,
                        )
                    }
                }
                _isLoadingTracks.value = false
            } catch (e: Throwable) {
                Timber.e("Failed to load tracks for audiobook ${bookId}: $e")
                _isLoadingTracks.value = false
            }
        }
    }

    fun onCacheButtonClick() {
        if (!prefsRepo.isPremium) {
            showUserMessage(FormattableString.from(R.string.premium_required_offline_playback))
            return
        }
        if (!mediaSource.isDownloadable) {
            return
        }
        when (cacheStatus.value) {
            NOT_CACHED -> {
                Timber.i("Caching tracks for \"${audiobook.value?.title}\"")
                if (mediaSource is HttpMediaSource && mediaSource.connectionState.value != ConnectionState.CONNECTED) {
                    showUserMessage(FormattableString.from(R.string.unable_to_cache_audiobook))
                } else {
                    cachedFileManager.downloadTracks(inputAudiobook.id, inputAudiobook.title)
                }
            }
            CACHED -> {
                Timber.i("Already cached. Uncache?")
                promptUserToUncache()
            }
            CACHING -> {
                Timber.i("Cancelling download: ${inputAudiobook.id}")
                cachedFileManager.cancelGroup(inputAudiobook.id)
            }
            else -> throw NoWhenBranchMatchedException("Unknown cache status. Don't know how to proceed")
        }
    }

    private fun showUserMessage(message: FormattableString) {
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
            cachedFileManager.deleteCachedBook(inputAudiobook.id, sourceId)
        }
    }

    fun pausePlayButtonClicked() {
        if (audiobook.value?.isCached == false) {
            if (mediaSource is HttpMediaSource && mediaSource.connectionState.value != ConnectionState.CONNECTED) {
                showUserMessage(FormattableString.from(R.string.cannot_play_media_no_server))
                return
            }
        }

        val pausePlayAction = {
            pausePlay(
                bookId = inputAudiobook.id.toString(),
                trackId = ACTIVE_TRACK,
                startTimeOffset = USE_SAVED_TRACK_PROGRESS,
                forcePlayFromMediaId = false
            )
        }
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
     *
     * [forcePlayFromMediaId] == true indicates to ignore playback state and play the book from the
     * given [trackId] and [startTimeOffset] provided, otherwise pause/play/resume depending on
     * playback state
     */
    private fun pausePlay(
        bookId: String,
        startTimeOffset: Long = USE_SAVED_TRACK_PROGRESS,
        trackId: Long = ACTIVE_TRACK,
        forcePlayFromMediaId: Boolean = false
    ) {
        if (mediaServiceConnection.isConnected.value != true) {
            Timber.e("MediaServiceConnection not connected")
            return
        }
        updateProgressIfChangingBook()

        val transportControls = mediaServiceConnection.transportControls ?: return

        val extras = Bundle().apply {
            putLong(KEY_START_TIME_TRACK_OFFSET, startTimeOffset)
            putLong(KEY_SEEK_TO_TRACK_WITH_ID, trackId)
        }
        Timber.i(
            "is this book playing? ${isBookInViewPlaying.value}, this this book active? ${isBookInViewActive.value}"
        )
        when {
            forcePlayFromMediaId -> transportControls.playFromMediaId(bookId, extras)
            isBookInViewPlaying.value == true -> transportControls.pause()
            isBookInViewActive.value == true -> transportControls.play()
            else -> transportControls.playFromMediaId(bookId, extras)
        }
    }

    /**
     * Check if there is an active audiobook which are about to be replaced by a different
     * audiobook and if so, make a network request to inform the server that playback has ended
     */
    private fun updateProgressIfChangingBook() {
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
        offset: Long = 0,
        trackId: Long = TRACK_NOT_FOUND.toLong(),
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
        if (mediaSource is HttpMediaSource) {
            mediaSource.connectionState.removeObserver(networkObserver)
        }
        super.onCleared()
    }

    fun toggleWatched() {
        val prompt = R.string.prompt_mark_as_watched
        showOptionsMenu(
            title = FormattableString.from(prompt),
            options = listOf(FormattableString.yes, FormattableString.no),
            listener = object : BottomChooserItemListener() {
                override fun onItemClicked(formattableString: FormattableString) {
                    if (formattableString == FormattableString.yes) {
                        setAudiobookWatched()
                    }
                    hideBottomSheet()
                }
            }
        )
    }

    private fun setAudiobookWatched() {
        Timber.i("Marking audiobook as watched")
        viewModelScope.launch {
            // Plex will set tracks as unwatched if their parent becomes unwatched, so no need
            // for [ITrackRepository.setWatched]
            trackRepository.markTracksInBookAsWatched(inputAudiobook.id)
            bookRepository.setWatched(inputAudiobook.id, mediaSource)
        }
    }

    private var _forceSyncInProgress = MutableLiveData(false)
    val forceSyncInProgress: LiveData<Boolean>
        get() = _forceSyncInProgress

    fun forceSyncBook(hasUserConfirmation: Boolean = false) {
        viewModelScope.launch {
            if (!hasUserConfirmation) {
                showOptionsMenu(
                    title = FormattableString.from(R.string.prompt_force_sync),
                    options = listOf(FormattableString.yes, FormattableString.no),
                    listener = object : BottomChooserItemListener() {
                        override fun onItemClicked(formattableString: FormattableString) {
                            if (formattableString == FormattableString.yes) {
                                forceSyncBook(hasUserConfirmation = true)
                            }
                            hideBottomSheet()
                        }
                    }
                )
                return@launch
            } else {
                Timber.i("Refreshing track data!!!")
                if (mediaSource !is HttpMediaSource) {
                    return@launch
                }
                if (mediaSource.connectionState.value != ConnectionState.CONNECTED) {
                    showUserMessage(FormattableString.from(R.string.cannot_sync_no_server))
                    return@launch
                }
                val audiobook = audiobook.value
                if (audiobook == null) {
                    showUserMessage(FormattableString.from(R.string.progress_sync_failed))
                    return@launch
                }
                _forceSyncInProgress.value = true
                val updatedTracks = trackRepository.syncTracksInBook(
                    bookId = audiobook.id,
                    source = mediaSource,
                    forceUseNetwork = true
                )
                val loadSucceeded = bookRepository.syncAudiobook(
                    source = mediaSource,
                    audiobook = audiobook,
                    tracks = updatedTracks,
                    forceNetwork = true
                )
                if (loadSucceeded) {
                    showUserMessage(FormattableString.from(R.string.progress_sync_successful))
                } else {
                    showUserMessage(FormattableString.from(R.string.progress_sync_failed))
                }
                _forceSyncInProgress.value = false
            }
        }
    }
}

