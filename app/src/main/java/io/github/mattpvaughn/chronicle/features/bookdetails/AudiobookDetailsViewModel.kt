package io.github.mattpvaughn.chronicle.features.bookdetails

import android.os.Bundle
import android.text.format.DateUtils
import android.util.Log
import androidx.lifecycle.*
import io.github.mattpvaughn.chronicle.BuildConfig
import io.github.mattpvaughn.chronicle.application.NetworkAwareViewModel
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.data.local.IBookRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import io.github.mattpvaughn.chronicle.data.model.getProgress
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME
import io.github.mattpvaughn.chronicle.data.plex.CachedFileManager
import io.github.mattpvaughn.chronicle.data.plex.PlexPrefsRepo
import io.github.mattpvaughn.chronicle.data.plex.model.getDuration
import io.github.mattpvaughn.chronicle.data.plex.ICachedFileManager.CacheStatus
import io.github.mattpvaughn.chronicle.data.plex.ICachedFileManager.CacheStatus.*
import io.github.mattpvaughn.chronicle.features.player.IMediaServiceConnection
import io.github.mattpvaughn.chronicle.features.player.MediaPlayerService.Companion.ACTIVE_TRACK
import io.github.mattpvaughn.chronicle.features.player.MediaPlayerService.Companion.KEY_PLAY_STARTING_WITH_TRACK_ID
import io.github.mattpvaughn.chronicle.features.player.MediaPlayerService.Companion.PLEX_STATE_STOPPED
import io.github.mattpvaughn.chronicle.features.player.ProgressUpdater
import io.github.mattpvaughn.chronicle.features.player.currentPlayBackPosition
import io.github.mattpvaughn.chronicle.features.player.id
import io.github.mattpvaughn.chronicle.injection.scopes.qualifiers.PerFragment
import io.github.mattpvaughn.chronicle.views.BottomSheetChooser
import kotlinx.coroutines.launch

@PerFragment
class AudiobookDetailsViewModel constructor(
    private val bookRepository: IBookRepository,
    private val trackRepository: ITrackRepository,
    private val cachedFileManager: CachedFileManager,
    plexPrefsRepo: PlexPrefsRepo,
    private val inputAudiobook: Audiobook,
    private val mediaServiceConnection: IMediaServiceConnection,
    private val progressUpdater: ProgressUpdater
) : NetworkAwareViewModel(plexPrefsRepo) {

    private var _audiobook = bookRepository.getAudiobook(inputAudiobook.id)
    val audiobook: LiveData<Audiobook>
        get() = _audiobook

    val tracks = trackRepository.getTracksForAudiobook(inputAudiobook.id)

    val cacheStatus = MediatorLiveData<CacheStatus>()
    private var _cacheStatusManual = MutableLiveData<CacheStatus>(if (inputAudiobook.isCached) CACHED else NOT_CACHED)
    private val _cacheStatusTracks = Transformations.map(tracks) {
        when {
            it.isEmpty() -> _cacheStatusManual.value
            it.all { track -> track.cached } -> CACHED
            it.none { track -> track.cached } -> NOT_CACHED
            else -> _cacheStatusManual.value
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

    val activeTrackId: LiveData<Int> = Transformations.map(mediaServiceConnection.nowPlaying) {
        if (it.id.isNullOrEmpty()) {
            -1
        } else {
            checkNotNull(it.id).toInt()
        }
    }

    val progressString = Transformations.map(tracks) {
        return@map DateUtils.formatElapsedTime(StringBuilder(), it.getProgress() / 1000)
    }

    val durationString = Transformations.map(audiobook) {
        return@map DateUtils.formatElapsedTime(StringBuilder(), it.duration / 1000)
    }

    private var _isLoadingTracks = MutableLiveData<Boolean>(false)
    val isLoadingTracks: LiveData<Boolean>
        get() = _isLoadingTracks

    private var _bottomSheetOptions = MutableLiveData<List<String>>(emptyList())
    val bottomSheetOptions: LiveData<List<String>>
        get() = _bottomSheetOptions

    private var _bottomOptionsListener =
        MutableLiveData<BottomSheetChooser.ItemSelectedListener>(BottomSheetChooser.ItemSelectedListener.emptyListener)
    val bottomOptionsListener: LiveData<BottomSheetChooser.ItemSelectedListener>
        get() = _bottomOptionsListener

    private var _bottomSheetTitle = MutableLiveData<String>("Title")
    val bottomSheetTitle: LiveData<String>
        get() = _bottomSheetTitle

    private var _showBottomSheet = MutableLiveData<Boolean>(false)
    val showBottomSheet: LiveData<Boolean>
        get() = _showBottomSheet

    // The maximum number of lines to shown in the info section
    private val lineCountSummaryMinimized = 5
    private val lineCountSummaryMaximized = Int.MAX_VALUE
    private var _summaryLinesShown = MutableLiveData<Int>(lineCountSummaryMinimized)
    val summaryLinesShown: LiveData<Int>
        get() = _summaryLinesShown

    val showSummary = Transformations.map(audiobook) {
        it.summary.isNotEmpty()
    }

    val isExpanded = Transformations.map(summaryLinesShown) { lines ->
        return@map lines == lineCountSummaryMaximized
    }

    fun onToggleSummaryView() {
        _summaryLinesShown.value =
            if (_summaryLinesShown.value == lineCountSummaryMinimized) lineCountSummaryMaximized else lineCountSummaryMinimized
    }

    private val networkObserver = Observer<Boolean> { isLoading ->
        if (!isLoading) {
            refreshTracks(inputAudiobook.id)
        }
    }

    init {
        isLoading.observeForever(networkObserver)

        // Create a MediatorLiveData from _cacheStatusTracks and _cacheStatusManual
        cacheStatus.addSource(_cacheStatusTracks) { cacheStatus.value = it }
        cacheStatus.addSource(_cacheStatusManual) { cacheStatus.value = it }
    }

    private fun refreshTracks(bookId: Int) {
        viewModelScope.launch {
            try {
                // Only replace track view w/ loading view if we have no tracks
                if (tracks.value?.isNullOrEmpty() == true) {
                    _isLoadingTracks.value = true
                }
                val tracks = trackRepository.loadTracksForAudiobook(bookId)
                bookRepository.updateTrackData(bookId, tracks.getDuration(), tracks.size)
                _isLoadingTracks.value = false
            } catch (e: Exception) {
                Log.e(APP_NAME, "Failed to load tracks for audiobook $bookId: $e")
                _isLoadingTracks.value = false
            }
        }
    }

    private fun cancelCaching() {
        // Cancel queued downloads
        cachedFileManager.cancelCaching()
        _cacheStatusManual.postValue(NOT_CACHED)
    }

    private fun notifyUser(s: String) {
        Log.i(APP_NAME, s)
        if (!BuildConfig.DEBUG) {
            throw TODO("I am a bad boy! I write method stubs and then don't finish them!")
        }
    }

    fun onCacheButtonClick() {
        when (cacheStatus.value) {
            NOT_CACHED -> {
                Log.i(APP_NAME, "Caching tracks: ${audiobook.value?.title}")
                _cacheStatusManual.postValue(CACHING)
                cachedFileManager.downloadTracks(tracks.value ?: emptyList())
            }
            CACHED -> promptUserToUncache()
            CACHING -> cancelCaching()
            else -> throw NoWhenBranchMatchedException("Unknown cache status. Don't know how to proceed")
        }
    }

    private fun promptUserToUncache() {
        showOptionsMenu(
            title = "Delete cached files?",
            options = listOf("Yes", "No"),
            listener = object : BottomSheetChooser.ItemSelectedListener {
                override fun onItemSelected(itemName: String) {
                    when (itemName) {
                        "Yes" -> uncacheFiles()
                        "No" -> { /* Do nothing */
                        }
                        else -> throw NoWhenBranchMatchedException("Unknown option selected!")
                    }
                    _showBottomSheet.postValue(false)
                }
            }
        )
    }

    private fun uncacheFiles() {
        cachedFileManager.uncacheTracks(inputAudiobook.id, tracks.value ?: emptyList())
        _cacheStatusManual.postValue(NOT_CACHED)
    }

    fun play() {
        if (mediaServiceConnection.isConnected.value != false) {
            checkNotNull(audiobook.value) { "Tried to play null audiobook!" }
            audiobook.value?.let { book ->
                playMediaId(book.id.toString())
            }
        }
    }

    private fun playMediaId(bookId: String) {
        // Check if media is already being played- if it is, inform plex that it's done streaming
        val currentlyPlayingTrackId = mediaServiceConnection.nowPlaying.value?.id
        Log.i(APP_NAME, "Currently playing is $currentlyPlayingTrackId")
        val isChangingBooks = if (currentlyPlayingTrackId.isNullOrEmpty()) {
            false
        } else {
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
                        state.currentPlayBackPosition
                    )
                }
            }
        }

        val extras = Bundle().apply {
            putInt(KEY_PLAY_STARTING_WITH_TRACK_ID, ACTIVE_TRACK)
        }

        Log.i(APP_NAME, "Playing!")
        mediaServiceConnection.transportControls?.playFromMediaId(bookId, extras)
    }


    fun jumpToTrack(track: MediaItemTrack) {
        val bookId = track.parentKey
        mediaServiceConnection.transportControls?.playFromMediaId(
            bookId.toString(),
            Bundle().apply { this.putInt(KEY_PLAY_STARTING_WITH_TRACK_ID, track.id) }
        )
    }

    private fun showOptionsMenu(
        title: String,
        options: List<String>,
        listener: BottomSheetChooser.ItemSelectedListener
    ) {
        _showBottomSheet.postValue(true)
        _bottomSheetTitle.postValue(title)
        _bottomOptionsListener.postValue(listener)
        _bottomSheetOptions.postValue(options)
    }

    override fun onCleared() {
        mediaServiceConnection.disconnect()
        isLoading.removeObserver(networkObserver)
        super.onCleared()
    }

}