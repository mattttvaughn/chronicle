package io.github.mattpvaughn.chronicle.features.currentlyplaying

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.format.DateUtils
import android.util.Log
import androidx.lifecycle.*
import io.github.mattpvaughn.chronicle.BuildConfig
import io.github.mattpvaughn.chronicle.application.MILLIS_PER_SECOND
import io.github.mattpvaughn.chronicle.application.NetworkAwareViewModel
import io.github.mattpvaughn.chronicle.application.SECONDS_PER_MINUTE
import io.github.mattpvaughn.chronicle.data.local.IBookRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository
import io.github.mattpvaughn.chronicle.data.model.*
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME
import io.github.mattpvaughn.chronicle.data.plex.PlexPrefsRepo
import io.github.mattpvaughn.chronicle.data.plex.model.getDuration
import io.github.mattpvaughn.chronicle.features.player.*
import io.github.mattpvaughn.chronicle.features.player.MediaPlayerService.Companion.ACTIVE_TRACK
import io.github.mattpvaughn.chronicle.features.player.MediaPlayerService.Companion.KEY_PLAY_STARTING_WITH_TRACK_ID
import io.github.mattpvaughn.chronicle.features.settings.PrefsRepo
import io.github.mattpvaughn.chronicle.util.observeOnce
import io.github.mattpvaughn.chronicle.views.BottomSheetChooser.ItemSelectedListener
import io.github.mattpvaughn.chronicle.views.BottomSheetChooser.ItemSelectedListener.Companion.emptyListener
import kotlinx.coroutines.launch
import javax.inject.Inject


class CurrentlyPlayingViewModel(
    private val bookRepository: IBookRepository,
    private val trackRepository: ITrackRepository,
    private val inputAudiobookId: Int,
    plexPrefsRepo: PlexPrefsRepo,
    private val prefsRepo: PrefsRepo,
    private val mediaServiceConnection: MediaServiceConnection
) : NetworkAwareViewModel(plexPrefsRepo) {

    class Factory @Inject constructor(
        private val bookRepository: IBookRepository,
        private val trackRepository: ITrackRepository,
        private val plexPrefsRepo: PlexPrefsRepo,
        private val prefsRepo: PrefsRepo,
        private val mediaServiceConnection: MediaServiceConnection
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CurrentlyPlayingViewModel::class.java)) {
                return CurrentlyPlayingViewModel(
                    bookRepository,
                    trackRepository,
                    0,
                    plexPrefsRepo,
                    prefsRepo,
                    mediaServiceConnection
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewHolder class")
        }
    }

    private var audiobookId = MutableLiveData<Int>()

    val audiobook: LiveData<Audiobook> = Transformations.switchMap(audiobookId) { id ->
        if (id == EMPTY_AUDIOBOOK.id) {
            emptyAudiobook
        } else {
            bookRepository.getAudiobook(id)
        }
    }

    private val emptyAudiobook = MutableLiveData(EMPTY_AUDIOBOOK)
    private val emptyTrackList = MutableLiveData<List<MediaItemTrack>>(emptyList())

    val tracks: LiveData<List<MediaItemTrack>> = Transformations.switchMap(audiobookId) { id ->
        if (id == EMPTY_AUDIOBOOK.id) {
            emptyTrackList
        } else {
            trackRepository.getTracksForAudiobook(id)
        }
    }


    private var _speed = MutableLiveData<Float>(prefsRepo.playbackSpeed)
    val speed: LiveData<Float>
        get() = _speed

    val activeTrackId: LiveData<Int> = Transformations.map(mediaServiceConnection.nowPlaying) {
        if (it.id.isNullOrEmpty()) {
            -1
        } else {
            it.id!!.toInt()
        }
    }

    val currentTrack = Transformations.map(tracks) { trackList ->
        trackList.getActiveTrack()
    }

    private var _isSleepTimerActive = MutableLiveData<Boolean>(false)
    val isSleepTimerActive: LiveData<Boolean>
        get() = _isSleepTimerActive

    private var _sleepTimerTimeRemaining = MutableLiveData<Long>()
    val sleepTimerTimeRemaining: LiveData<Long>
        get() = _sleepTimerTimeRemaining

    val isPlaying: LiveData<Boolean> =
        Transformations.map(mediaServiceConnection.playbackState) { state ->
            Log.i(APP_NAME, "Is playing? ${state.isPlaying}")
            return@map state.isPlaying
        }

    val chapterProgress = Transformations.map(currentTrack) { track ->
        return@map DateUtils.formatElapsedTime(
            StringBuilder(),
            track.progress / 1000
        )
    }

    val chapterDuration = Transformations.map(currentTrack) { track ->
        return@map DateUtils.formatElapsedTime(StringBuilder(), track.duration / 1000)
    }

    val bookProgressString = Transformations.map(tracks) {
        return@map DateUtils.formatElapsedTime(StringBuilder(), it.getProgress() / 1000)
    }

    val bookDurationString = Transformations.map(audiobook) {
        return@map DateUtils.formatElapsedTime(StringBuilder(), it.duration / 1000)
    }

    private var _isLoadingTracks = MutableLiveData<Boolean>(false)
    val isLoadingTracks: LiveData<Boolean>
        get() = _isLoadingTracks

    private var _bottomSheetOptions = MutableLiveData<List<String>>(emptyList())
    val bottomSheetOptions: LiveData<List<String>>
        get() = _bottomSheetOptions

    private var _bottomOptionsListener = MutableLiveData<ItemSelectedListener>(emptyListener)
    val bottomOptionsListener: LiveData<ItemSelectedListener>
        get() = _bottomOptionsListener

    private var _bottomSheetTitle = MutableLiveData<String>("Title")
    val bottomSheetTitle: LiveData<String>
        get() = _bottomSheetTitle

    private var _showBottomSheet = MutableLiveData<Boolean>(false)
    val showBottomSheet: LiveData<Boolean>
        get() = _showBottomSheet

    // Sleep timer
    private var _sleepTimerOptions = MutableLiveData<List<String>>(emptyList())
    val sleepTimerOptions: LiveData<List<String>>
        get() = _sleepTimerOptions

    private var _sleepTimerListener = MutableLiveData<ItemSelectedListener>(emptyListener)
    val sleepTimerListener: LiveData<ItemSelectedListener>
        get() = _sleepTimerListener

    private var _sleepTimerTitle = MutableLiveData<String>("Title")
    val sleepTimerTitle: LiveData<String>
        get() = _sleepTimerTitle

    private var _showSleepTimer = MutableLiveData<Boolean>(false)
    val showSleepTimer: LiveData<Boolean>
        get() = _showSleepTimer

    private val prefsChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            PrefsRepo.KEY_PLAYBACK_SPEED -> _speed.postValue(prefsRepo.playbackSpeed)
        }
    }

    private val networkObserver = Observer<Boolean> { isLoading ->
        if (!isLoading) {
            refreshTracks(inputAudiobookId)
        }
    }

    private val playbackObserver = Observer<MediaMetadataCompat> { metadata ->
        metadata.id?.let { trackId ->
            if (trackId.isNotEmpty()) {
                setAudiobook(trackId.toInt())
            }
        }
    }

    private fun setAudiobook(trackId: Int) {
        viewModelScope.launch {
            audiobookId.postValue(trackRepository.getBookIdForTrack(trackId))
        }
    }

    init {
        mediaServiceConnection.nowPlaying.observeForever(playbackObserver)

        isLoading.observeForever(networkObserver)

        // Listen for changes in SharedPreferences that could effect playback
        prefsRepo.registerPrefsListener(prefsChangeListener)
    }

    private fun refreshTracks(bookId: Int) {
        viewModelScope.launch {
            try {
                // Only replace track view w/ loading view if we have no tracks
                if (tracks.value?.size == null) {
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

    private fun notifyUser(s: String) {
        Log.i(APP_NAME, s)
        if (!BuildConfig.DEBUG) {
            throw TODO("I am a bad boy! I write method stubs and then don't finish them!")
        }
    }

    fun play() {
        if (mediaServiceConnection.isConnected.value != false) {
            checkNotNull(audiobook.value) { "Tried to play null audiobook!" }
            playMediaId(audiobook.value!!.id.toString())
        }
    }

    private fun playMediaId(bookId: String) {
        val transportControls = mediaServiceConnection.transportControls

        val extras = Bundle().apply { putInt(KEY_PLAY_STARTING_WITH_TRACK_ID, ACTIVE_TRACK) }
        mediaServiceConnection.playbackState.value?.let { playbackState ->
            if (playbackState.isPlaying) {
                Log.i(APP_NAME, "Pausing!")
                transportControls?.pause()
            } else {
                Log.i(APP_NAME, "Playing!")
                transportControls?.playFromMediaId(bookId, extras)
            }
        }
    }

    fun skipForwards() {
        seekRelative(SKIP_FORWARDS, SKIP_FORWARDS_DURATION_MS)
    }

    fun skipBackwards() {
        seekRelative(SKIP_BACKWARDS, SKIP_BACKWARDS_DURATION_MS)
    }

    private fun seekRelative(action: PlaybackStateCompat.CustomAction, offset: Long) {
        if (mediaServiceConnection.nowPlaying.value != NOTHING_PLAYING) {
            // Service will be alive, so we can let it handle the action
            Log.i(APP_NAME, "Sending custom action!")
            mediaServiceConnection.transportControls?.sendCustomAction(action, null)
        } else {
            Log.i(APP_NAME, "Updating progress manually!")
            // Service is not alive, so update track repo directly
            tracks.observeOnce(Observer { _tracks ->
                viewModelScope.launch {
                    val currentTrack = _tracks.getActiveTrack()
                    val manager = MediaPlayerService.TrackListStateManager()
                    manager.trackList = _tracks
                    manager.currentPosition = currentTrack.progress
                    manager.currentTrackIndex = _tracks.indexOf(currentTrack)
                    manager.seekByRelative(offset)
                    val updatedTrack = _tracks[manager.currentTrackIndex]
                    trackRepository.updateTrackProgress(
                        manager.currentPosition,
                        updatedTrack.id,
                        System.currentTimeMillis()
                    )
                }
            })
        }
    }

    fun jumpToTrack(track: MediaItemTrack) {
        val bookId = track.parentKey
        mediaServiceConnection.transportControls?.playFromMediaId(
            bookId.toString(),
            Bundle().apply { this.putInt(KEY_PLAY_STARTING_WITH_TRACK_ID, track.id) }
        )
    }

    fun showSleepTimerOptions() {
        _showSleepTimer.postValue(true)
        val options = if (isSleepTimerActive.value == true) {
            listOf("+5 minutes", "End of chapter", "Cancel")
        } else {
            _sleepTimerTitle.postValue("Sleep timer")
            listOf("5 minutes", "15 minutes", "30 minutes", "40 minutes", "End of chapter")
        }
        _sleepTimerOptions.postValue(options)
        _sleepTimerListener.postValue(object : ItemSelectedListener {
            override fun onItemSelected(itemName: String) {
                val actionPair = when (itemName) {
                    "5 minutes", "15 minutes", "30 minutes", "40 minutes" -> {
                        val duration =
                            itemName.substringBefore(" ").toLong() * SECONDS_PER_MINUTE * MILLIS_PER_SECOND
                        START_SLEEP_TIMER_STRING to duration
                    }
                    "End of chapter" -> {
                        val track = tracks.value!!.getActiveTrack()
                        val duration =
                            ((track.duration - track.progress) / prefsRepo.playbackSpeed).toLong()
                        START_SLEEP_TIMER_STRING to duration
                    }
                    "+5 minutes" -> {
                        val additionalTime = 5 * SECONDS_PER_MINUTE * MILLIS_PER_SECOND
                        SLEEP_TIMER_ACTION_EXTEND to additionalTime
                    }
                    "Cancel" -> {
                        SLEEP_TIMER_ACTION_CANCEL to 0L
                    }
                    else -> throw NoWhenBranchMatchedException("Unknown duration picked for sleep timer")
                }
                mediaServiceConnection.transportControls?.sendCustomAction(
                    actionPair.first,
                    Bundle().apply {
                        putLong(
                            KEY_SLEEP_TIMER_DURATION_MILLIS,
                            actionPair.second
                        )
                    }
                )
                _showSleepTimer.postValue(false)
            }
        })

    }

    fun showSpeedChooser() {
        showOptionsMenu(
            title = "Playback Speed",
            options = listOf("0.5x", "0.7x", "1.0x", "1.2x", "1.5x", "2.0x", "3.0x"),
            listener = object : ItemSelectedListener {
                override fun onItemSelected(itemName: String) {
                    when (itemName) {
                        "0.5x", "0.7x", "1.0x", "1.2x", "1.5x", "2.0x", "3.0x" -> {
                            val playbackSpeed: Float = itemName.substringBefore("x").toFloat()
                            prefsRepo.playbackSpeed = playbackSpeed
                        }
                        else -> throw NoWhenBranchMatchedException("Unknown playback speed selected")
                    }
                    _showBottomSheet.postValue(false)
                }
            }
        )
    }

    private fun showOptionsMenu(
        title: String,
        options: List<String>,
        listener: ItemSelectedListener
    ) {
        _showBottomSheet.postValue(true)
        _bottomSheetTitle.postValue(title)
        _bottomOptionsListener.postValue(listener)
        _bottomSheetOptions.postValue(options)
    }


    val onUpdateSleepTimer = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null) {
                val timeLeftMillis = intent.getLongExtra(UPDATE_SLEEP_TIMER_STRING, 0L)
                val shouldSleepSleepTimerBeActive = timeLeftMillis > 0L
                _isSleepTimerActive.postValue(shouldSleepSleepTimerBeActive)
                _sleepTimerTimeRemaining.postValue(timeLeftMillis)
                if (shouldSleepSleepTimerBeActive) {
                    _sleepTimerTitle.postValue("Sleep timer: ${DateUtils.formatElapsedTime(timeLeftMillis / MILLIS_PER_SECOND)} remaining")
                } else {
                    _sleepTimerTitle.postValue("Sleep timer")
                }
            }
        }
    }

    override fun onCleared() {
        mediaServiceConnection.nowPlaying.removeObserver(playbackObserver)
        prefsRepo.unRegisterPrefsListener(prefsChangeListener)
        mediaServiceConnection.disconnect()
        isLoading.removeObserver(networkObserver)
        super.onCleared()
    }

    fun seekTo(progress: Int) {
        mediaServiceConnection.transportControls?.seekTo(progress.toLong())
    }
}