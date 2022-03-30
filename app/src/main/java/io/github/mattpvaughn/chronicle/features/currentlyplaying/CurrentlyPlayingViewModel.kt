package io.github.mattpvaughn.chronicle.features.currentlyplaying

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED
import android.text.format.DateUtils
import android.view.Gravity
import android.widget.Toast
import androidx.lifecycle.*
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.github.michaelbull.result.Ok
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.application.MILLIS_PER_SECOND
import io.github.mattpvaughn.chronicle.application.SECONDS_PER_MINUTE
import io.github.mattpvaughn.chronicle.data.local.IBookRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository.Companion.TRACK_NOT_FOUND
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import io.github.mattpvaughn.chronicle.data.model.*
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexConfig
import io.github.mattpvaughn.chronicle.data.sources.plex.model.getDuration
import io.github.mattpvaughn.chronicle.features.player.*
import io.github.mattpvaughn.chronicle.features.player.MediaPlayerService.Companion.ACTIVE_TRACK
import io.github.mattpvaughn.chronicle.features.player.MediaPlayerService.Companion.KEY_SEEK_TO_TRACK_WITH_ID
import io.github.mattpvaughn.chronicle.features.player.MediaPlayerService.Companion.KEY_START_TIME_TRACK_OFFSET
import io.github.mattpvaughn.chronicle.features.player.MediaPlayerService.Companion.USE_SAVED_TRACK_PROGRESS
import io.github.mattpvaughn.chronicle.features.player.SleepTimer.Companion.ARG_SLEEP_TIMER_ACTION
import io.github.mattpvaughn.chronicle.features.player.SleepTimer.Companion.ARG_SLEEP_TIMER_DURATION_MILLIS
import io.github.mattpvaughn.chronicle.features.player.SleepTimer.SleepTimerAction
import io.github.mattpvaughn.chronicle.features.player.SleepTimer.SleepTimerAction.*
import io.github.mattpvaughn.chronicle.util.*
import io.github.mattpvaughn.chronicle.views.BottomSheetChooser.*
import io.github.mattpvaughn.chronicle.views.BottomSheetChooser.BottomChooserState.Companion.EMPTY_BOTTOM_CHOOSER
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@ExperimentalCoroutinesApi
class CurrentlyPlayingViewModel(
    private val bookRepository: IBookRepository,
    private val trackRepository: ITrackRepository,
    private val localBroadcastManager: LocalBroadcastManager,
    private val mediaServiceConnection: MediaServiceConnection,
    private val prefsRepo: PrefsRepo,
    private val plexConfig: PlexConfig,
    private val currentlyPlaying: CurrentlyPlaying
) : ViewModel() {

    @Suppress("UNCHECKED_CAST")
    class Factory @Inject constructor(
        private val bookRepository: IBookRepository,
        private val trackRepository: ITrackRepository,
        private val localBroadcastManager: LocalBroadcastManager,
        private val mediaServiceConnection: MediaServiceConnection,
        private val prefsRepo: PrefsRepo,
        private val plexConfig: PlexConfig,
        private val currentlyPlaying: CurrentlyPlaying
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CurrentlyPlayingViewModel::class.java)) {
                return CurrentlyPlayingViewModel(
                    bookRepository,
                    trackRepository,
                    localBroadcastManager,
                    mediaServiceConnection,
                    prefsRepo,
                    plexConfig,
                    currentlyPlaying
                ) as T
            } else {
                throw IllegalArgumentException("Incorrect class type provided")
            }
        }
    }

    private var _showUserMessage = MutableLiveData<Event<String>>()
    val showUserMessage: LiveData<Event<String>>
        get() = _showUserMessage

    private var audiobookId = MutableLiveData(EMPTY_AUDIOBOOK.id)

    val audiobook: LiveData<Audiobook?> = Transformations.switchMap(audiobookId) { id ->
        if (id == EMPTY_AUDIOBOOK.id) {
            emptyAudiobook
        } else {
            bookRepository.getAudiobook(id)
        }
    }

    private val emptyAudiobook = MutableLiveData(EMPTY_AUDIOBOOK)
    private val emptyTrackList = MutableLiveData<List<MediaItemTrack>>(emptyList())

    // TODO: expose combined track/chapter bits in ViewModel as "windowSomething" instead of in xml
    val tracks: LiveData<List<MediaItemTrack>> = Transformations.switchMap(audiobookId) { id ->
        if (id == EMPTY_AUDIOBOOK.id) {
            emptyTrackList
        } else {
            trackRepository.getTracksForAudiobook(id)
        }
    }

    // Used to cache tracks.asChapterList when tracks changes
    private val tracksAsChaptersCache: LiveData<List<Chapter>> = mapAsync(tracks, viewModelScope) {
        it.asChapterList()
    }

    val chapters: DoubleLiveData<Audiobook?, List<Chapter>, List<Chapter>> =
        DoubleLiveData(
            audiobook, tracksAsChaptersCache
        ) { _audiobook: Audiobook?, _tracksAsChapters: List<Chapter>? ->
            if (_audiobook?.chapters?.isNotEmpty() == true) {
                // We would really prefer this because it doesn't have to be computed
                _audiobook.chapters
            } else {
                _tracksAsChapters ?: emptyList()
            }
        }

    private var _speed = MutableLiveData(prefsRepo.playbackSpeed)
    val speed: LiveData<Float>
        get() = _speed

    val activeTrackId: LiveData<Int> =
        Transformations.map(mediaServiceConnection.nowPlaying) { metadata ->
            metadata.takeIf { !it.id.isNullOrEmpty() }?.id?.toInt() ?: TRACK_NOT_FOUND
        }

    val currentTrack: LiveData<MediaItemTrack> =
        currentlyPlaying.track.asLiveData(viewModelScope.coroutineContext)

    val currentChapter = currentlyPlaying.chapter.asLiveData(viewModelScope.coroutineContext)

    val chapterProgress = currentlyPlaying.chapter.combine(currentlyPlaying.track)
    { chapter: Chapter, track: MediaItemTrack ->
        track.progress - chapter.startTimeOffset
    }.asLiveData(viewModelScope.coroutineContext)

    val chapterProgressString = Transformations.map(chapterProgress) { progress ->
        return@map DateUtils.formatElapsedTime(
            StringBuilder(),
            progress / 1000
        )
    }

    val chapterProgressForSlider = currentlyPlaying.chapter.combine(currentlyPlaying.track)
    { chapter: Chapter, track: MediaItemTrack ->
        track.progress - chapter.startTimeOffset
    }.filter { !isSliding }.asLiveData(viewModelScope.coroutineContext)

    val trackProgressForSlider = currentlyPlaying.track
        .filter { !isSliding }
        .map { it.progress }
        .asLiveData(viewModelScope.coroutineContext)

    val chapterDuration = Transformations.map(currentChapter) {
        return@map it.endTimeOffset - it.startTimeOffset
    }

    val chapterDurationString = Transformations.map(chapterDuration) { duration ->
        return@map DateUtils.formatElapsedTime(
            StringBuilder(),
            duration / 1000
        )
    }

    var isSliding = false

    private var _isSleepTimerActive = MutableLiveData(false)
    val isSleepTimerActive: LiveData<Boolean>
        get() = _isSleepTimerActive

    private var sleepTimerTimeRemaining = MutableLiveData(0L)

    val sleepTimerTimeRemainingString = Transformations.map(sleepTimerTimeRemaining) {
        return@map DateUtils.formatElapsedTime(StringBuilder(), it / 1000)
    }

    val isPlaying: LiveData<Boolean> =
        Transformations.map(mediaServiceConnection.playbackState) { state ->
            return@map state.isPlaying
        }

    val trackProgress = Transformations.map(currentTrack) { track ->
        return@map DateUtils.formatElapsedTime(
            StringBuilder(),
            track.progress / 1000
        )
    }

    val trackDuration = Transformations.map(currentTrack) { track ->
        return@map DateUtils.formatElapsedTime(StringBuilder(), track.duration / 1000)
    }

    val bookProgressString = Transformations.map(tracks) {
        return@map DateUtils.formatElapsedTime(StringBuilder(), it.getProgress() / 1000)
    }

    val bookDurationString = Transformations.map(audiobook) {
        return@map DateUtils.formatElapsedTime(StringBuilder(), it?.duration?.div(1000) ?: 0)
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

    private var _isLoadingTracks = MutableLiveData(false)
    val isLoadingTracks: LiveData<Boolean>
        get() = _isLoadingTracks

    private var _bottomChooserState = MutableLiveData(EMPTY_BOTTOM_CHOOSER)
    val bottomChooserState: LiveData<BottomChooserState>
        get() = _bottomChooserState

    private var _sleepTimerChooserState = MutableLiveData(EMPTY_BOTTOM_CHOOSER)
    val sleepTimerChooserState: LiveData<BottomChooserState>
        get() = _sleepTimerChooserState

    private var _jumpForwardsIcon = MutableLiveData(makeJumpForwardsIcon())
    val jumpForwardsIcon: LiveData<Int>
        get() = _jumpForwardsIcon

    private var _jumpBackwardsIcon = MutableLiveData(makeJumpBackwardsIcon())
    val jumpBackwardsIcon: LiveData<Int>
        get() = _jumpBackwardsIcon

    private val prefsChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            PrefsRepo.KEY_PLAYBACK_SPEED -> _speed.postValue(prefsRepo.playbackSpeed)
            PrefsRepo.KEY_JUMP_FORWARD_SECONDS -> _jumpForwardsIcon.value = makeJumpForwardsIcon()
            PrefsRepo.KEY_JUMP_BACKWARD_SECONDS -> _jumpBackwardsIcon.value = makeJumpBackwardsIcon()
        }
    }

    private val networkObserver = Observer<Boolean> { isConnected ->
        if (isConnected) {
            audiobookId.value?.let {
                refreshTracks(it)
            }
        }
    }

    private val playbackObserver = Observer<MediaMetadataCompat> { metadata ->
        if (metadata.id?.isEmpty() == false) {
            setAudiobook(metadata.id!!.toInt())
        }
    }

    private fun setAudiobook(trackId: Int) {
        val previousAudiobookId = audiobook.value?.id ?: NO_AUDIOBOOK_FOUND_ID
        viewModelScope.launch(Injector.get().unhandledExceptionHandler()) {
            // only update [audiobookId] when we see a new audiobook
            val potentiallyNewAudiobookId = trackRepository.getBookIdForTrack(trackId)
            if (potentiallyNewAudiobookId != previousAudiobookId) {
                audiobookId.postValue(potentiallyNewAudiobookId)
            }
        }
    }

    init {
        mediaServiceConnection.nowPlaying.observeForever(playbackObserver)
        plexConfig.isConnected.observeForever(networkObserver)

        // Listen for changes in SharedPreferences that could effect playback
        prefsRepo.registerPrefsListener(prefsChangeListener)
    }

    private fun refreshTracks(bookId: Int) {
        if (bookId == NO_AUDIOBOOK_FOUND_ID) {
            return
        }
        viewModelScope.launch(Injector.get().unhandledExceptionHandler()) {
            try {
                // Only replace track view w/ loading view if we have no tracks
                if (tracks.value?.size == null) {
                    _isLoadingTracks.value = true
                }
                val tracks = trackRepository.loadTracksForAudiobook(bookId)
                if (tracks is Ok) {
                    bookRepository.updateTrackData(
                        bookId,
                        tracks.value.getProgress(),
                        tracks.value.getDuration(),
                        tracks.value.size
                    )
                    audiobook.value?.let {
                        bookRepository.syncAudiobook(it, tracks.value)
                    }
                }
                _isLoadingTracks.value = false
            } catch (e: Throwable) {
                Timber.e("Failed to load tracks for audiobook $bookId: $e")
                _isLoadingTracks.value = false
            }
        }
    }

    fun play() {
        if (mediaServiceConnection.isConnected.value == true) {
            if (audiobook.value == null) {
                Timber.e("Tried to play null audiobook!")
                _showUserMessage.postEvent("Audiobook is null. Try restarting the app and trying again")
                return
            }
            pausePlay(
                bookId = audiobook.value!!.id.toString(),
                trackId = ACTIVE_TRACK,
                startTimeOffset = ACTIVE_TRACK,
                forcePlay = false
            )
        }
    }

    private fun pausePlay(
        bookId: String,
        startTimeOffset: Long = USE_SAVED_TRACK_PROGRESS,
        forcePlay: Boolean = false,
        trackId: Long = ACTIVE_TRACK
    ) {
        val transportControls = mediaServiceConnection.transportControls

        val extras = Bundle().apply {
            putLong(KEY_START_TIME_TRACK_OFFSET, startTimeOffset)
            putLong(KEY_SEEK_TO_TRACK_WITH_ID, trackId)
        }
        if (transportControls != null) {
            mediaServiceConnection.playbackState.value?.let { playbackState ->
                when {
                    forcePlay -> transportControls.playFromMediaId(bookId, extras)
                    playbackState.state == STATE_PAUSED -> transportControls.play()
                    playbackState.isPlaying -> transportControls.pause()
                    else -> {
                    } // do nothing?
                }
            }
        }
    }

    fun skipToNext() {
        skipToChapter(SKIP_TO_NEXT, forward = true)

    }

    fun skipToPrevious() {
        skipToChapter(SKIP_TO_PREVIOUS, forward = false)
    }

    private fun skipToChapter(action: PlaybackStateCompat.CustomAction, forward: Boolean) {
        val transportControls = mediaServiceConnection.transportControls
        mediaServiceConnection.let { connection ->
            if (connection.nowPlaying.value != NOTHING_PLAYING) {
                // Service will be alive, so we can let it handle the action
                Timber.i("Seeking!")
                transportControls?.sendCustomAction(action, null)
            } else {
                val currentChapterIndex = currentlyPlaying.book.value.chapters.indexOf(currentlyPlaying.chapter.value)
                var skipToChapterIndex : Int
                if(forward) {
                    skipToChapterIndex = currentChapterIndex + 1
                    if(skipToChapterIndex < currentlyPlaying.book.value.chapters.size) {
                        val skipToChapter = currentlyPlaying.book.value.chapters[skipToChapterIndex]
                        jumpToChapter(skipToChapter.startTimeOffset,currentlyPlaying.track.value.id,hasUserConfirmation = true)
                    } else {
                        val toast = Toast.makeText(
                            Injector.get().applicationContext(), R.string.skip_forwards_reached_last_chapter,
                            Toast.LENGTH_LONG)
                        toast.setGravity(Gravity.BOTTOM,0,200)
                        toast.show()
                    }
                } else {
                    skipToChapterIndex = currentChapterIndex - 1
                    if(skipToChapterIndex < 0) skipToChapterIndex = 0
                    val skipToChapter = currentlyPlaying.book.value.chapters[skipToChapterIndex]
                    jumpToChapter(skipToChapter.startTimeOffset, currentlyPlaying.track.value.id,hasUserConfirmation = true)
                }
            }
        }
    }

    fun makeJumpForwardsIcon(): Int {
        return when (prefsRepo.jumpForwardSeconds) {
            10L -> R.drawable.ic_forward_10_white
            15L -> R.drawable.ic_forward_15_white
            20L -> R.drawable.ic_forward_20_white
            30L -> R.drawable.ic_forward_30_white
            60L -> R.drawable.ic_forward_60_white
            90L -> R.drawable.ic_forward_90_white
            else -> R.drawable.ic_forward_30_white
        }
    }

    fun makeJumpBackwardsIcon(): Int {
        return when (prefsRepo.jumpBackwardSeconds) {
            10L -> R.drawable.ic_replay_10_white
            15L -> R.drawable.ic_replay_15_white
            20L -> R.drawable.ic_replay_20_white
            30L -> R.drawable.ic_replay_30_white
            60L -> R.drawable.ic_replay_60_white
            90L -> R.drawable.ic_replay_90_white
            else -> R.drawable.ic_replay_10_white
        }
    }

    fun skipForwards() {
        seekRelative(makeSkipForward(prefsRepo), prefsRepo.jumpForwardSeconds * MILLIS_PER_SECOND)
    }

    fun skipBackwards() {
        seekRelative(makeSkipBackward(prefsRepo), prefsRepo.jumpBackwardSeconds * MILLIS_PER_SECOND * -1)
    }

    private fun seekRelative(action: PlaybackStateCompat.CustomAction, offset: Long) {
        val transportControls = mediaServiceConnection.transportControls
        mediaServiceConnection.let { connection ->
            if (connection.nowPlaying.value != NOTHING_PLAYING) {
                // Service will be alive, so we can let it handle the action
                Timber.i("Seeking!")
                transportControls?.sendCustomAction(action, null)
            } else {
                Timber.i("Updating DB progress!")
                // Service is not alive, so update track repo directly
                tracks.observeOnce { _tracks ->
                    viewModelScope.launch(Injector.get().unhandledExceptionHandler()) {
                        // don't bother seeking if there aren't any files
                        if (_tracks.isEmpty()) {
                            return@launch
                        }
                        val manager = TrackListStateManager()
                        manager.trackList = _tracks
                        manager.seekToActiveTrack()
                        manager.seekByRelative(offset)
                        val updatedTrack = _tracks[manager.currentTrackIndex]
                        trackRepository.updateTrackProgress(
                            manager.currentBookPosition,
                            updatedTrack.id,
                            System.currentTimeMillis()
                        )
                    }
                }
            }
        }
    }


    /** Jumps to a given track with [MediaItemTrack.id] == [trackId] */
    fun jumpToChapter(
        startTimeOffset: Long = 0,
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
                            FormattableString.yes -> jumpToChapter(startTimeOffset, trackId, true)
                            FormattableString.no -> Unit
                            else -> throw NoWhenBranchMatchedException()
                        }
                        hideOptionsMenu()
                    }
                })
            return
        }

        val jumpToChapterAction = {
            audiobook.value?.let { book ->
                pausePlay(
                    book.id.toString(),
                    startTimeOffset = startTimeOffset,
                    trackId = trackId.toLong(),
                    forcePlay = true
                )
            }
        }
        if (mediaServiceConnection.isConnected.value != true) {
            mediaServiceConnection.connect(onConnected = jumpToChapterAction)
        } else {
            jumpToChapterAction()
        }
    }

    fun showSleepTimerOptions() {
        val title = if (isSleepTimerActive.value != true) {
            FormattableString.from(R.string.sleep_timer)
        } else {
            FormattableString.ResourceString(
                R.string.sleep_timer_active_title,
                placeHolderStrings = listOf(sleepTimerTimeRemainingString.value ?: "<Error>")
            )
        }
        val options = if (isSleepTimerActive.value == true) {
            listOf(
                FormattableString.from(R.string.sleep_timer_append),
                FormattableString.from(R.string.sleep_timer_duration_end_of_chapter),
                FormattableString.from(R.string.cancel)
            )
        } else {
            listOf(
                FormattableString.from(R.string.sleep_timer_duration_5_minutes),
                FormattableString.from(R.string.sleep_timer_duration_15_minutes),
                FormattableString.from(R.string.sleep_timer_duration_30_minutes),
                FormattableString.from(R.string.sleep_timer_duration_40_minutes),
                FormattableString.from(R.string.sleep_timer_duration_60_minutes),
                FormattableString.from(R.string.sleep_timer_duration_90_minutes),
                FormattableString.from(R.string.sleep_timer_duration_120_minutes),
                FormattableString.from(R.string.sleep_timer_duration_end_of_chapter)
            )
        }
        val listener = object : BottomChooserListener {
            override fun onItemClicked(formattableString: FormattableString) {
                check(formattableString is FormattableString.ResourceString)

                val actionPair: Pair<SleepTimerAction, Long> = when (formattableString.stringRes) {
                    R.string.sleep_timer_duration_5_minutes -> {
                        val duration = 5 * SECONDS_PER_MINUTE * MILLIS_PER_SECOND
                        BEGIN to duration
                    }
                    R.string.sleep_timer_duration_15_minutes -> {
                        val duration = 15 * SECONDS_PER_MINUTE * MILLIS_PER_SECOND
                        BEGIN to duration
                    }
                    R.string.sleep_timer_duration_30_minutes -> {
                        val duration = 30 * SECONDS_PER_MINUTE * MILLIS_PER_SECOND
                        BEGIN to duration
                    }
                    R.string.sleep_timer_duration_40_minutes -> {
                        val duration = 40 * SECONDS_PER_MINUTE * MILLIS_PER_SECOND
                        BEGIN to duration
                    }
                    R.string.sleep_timer_duration_60_minutes -> {
                        val duration = 60 * SECONDS_PER_MINUTE * MILLIS_PER_SECOND
                        BEGIN to duration
                    }
                    R.string.sleep_timer_duration_90_minutes -> {
                        val duration = 90 * SECONDS_PER_MINUTE * MILLIS_PER_SECOND
                        BEGIN to duration
                    }
                    R.string.sleep_timer_duration_120_minutes -> {
                        val duration = 120 * SECONDS_PER_MINUTE * MILLIS_PER_SECOND
                        BEGIN to duration
                    }
                    R.string.sleep_timer_duration_end_of_chapter -> {
                        val duration = ((chapterDuration.value ?: 0L) - (chapterProgress.value
                            ?: 0L) / prefsRepo.playbackSpeed).toLong()
                        BEGIN to duration
                    }
                    R.string.sleep_timer_append -> {
                        val additionalTime = 5 * SECONDS_PER_MINUTE * MILLIS_PER_SECOND
                        EXTEND to additionalTime
                    }
                    R.string.cancel -> {
                        setSleepTimerTitle(FormattableString.from(R.string.sleep_timer))
                        CANCEL to 0L
                    }
                    else -> throw NoWhenBranchMatchedException("Unknown duration picked for sleep timer")
                }
                hideSleepTimerChooser()
                val sleepTimerIntent = Intent(SleepTimer.ACTION_SLEEP_TIMER_CHANGE).apply {
                    putExtra(ARG_SLEEP_TIMER_ACTION, actionPair.first)
                    putExtra(ARG_SLEEP_TIMER_DURATION_MILLIS, actionPair.second)
                }
                localBroadcastManager.sendBroadcast(sleepTimerIntent)
            }

            override fun onChooserClosed(wasBackgroundClicked: Boolean) {
                if (wasBackgroundClicked) {
                    hideSleepTimerChooser()
                }
            }
        }

        _sleepTimerChooserState.postValue(
            BottomChooserState(
                title = title,
                options = options,
                listener = listener,
                shouldShow = true
            )
        )
    }

    fun showSpeedChooser() {
        if (!prefsRepo.isPremium) {
            _showUserMessage.postEvent("Error: variable playback speed is a premium feature")
            return
        }
        showOptionsMenu(
            title = FormattableString.from(R.string.playback_speed_title),
            options = listOf(
                FormattableString.from(R.string.playback_speed_0_5x),
                FormattableString.from(R.string.playback_speed_0_7x),
                FormattableString.from(R.string.playback_speed_1_0x),
                FormattableString.from(R.string.playback_speed_1_2x),
                FormattableString.from(R.string.playback_speed_1_5x),
                FormattableString.from(R.string.playback_speed_1_7x),
                FormattableString.from(R.string.playback_speed_2_0x),
                FormattableString.from(R.string.playback_speed_3_0x)
            ),
            listener = object : BottomChooserItemListener() {
                override fun onItemClicked(formattableString: FormattableString) {
                    check(formattableString is FormattableString.ResourceString)

                    prefsRepo.playbackSpeed = when (formattableString.stringRes) {
                        R.string.playback_speed_0_5x -> 0.5f
                        R.string.playback_speed_0_7x -> 0.7f
                        R.string.playback_speed_1_0x -> 1.0f
                        R.string.playback_speed_1_2x -> 1.2f
                        R.string.playback_speed_1_5x -> 1.5f
                        R.string.playback_speed_1_7x -> 1.7f
                        R.string.playback_speed_2_0x -> 2.0f
                        R.string.playback_speed_3_0x -> 3.0f
                        else -> throw NoWhenBranchMatchedException("Unknown playback speed selected")
                    }
                    hideOptionsMenu()
                }
            }
        )
    }

    private fun hideSleepTimerChooser() {
        _sleepTimerChooserState.postValue(
            _sleepTimerChooserState.value?.copy(shouldShow = false) ?: EMPTY_BOTTOM_CHOOSER
        )
    }

    private fun hideOptionsMenu() {
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


    val onUpdateSleepTimer = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null || !intent.hasExtra(ARG_SLEEP_TIMER_DURATION_MILLIS)) {
                return
            }
            val timeLeftMillis = intent.getLongExtra(ARG_SLEEP_TIMER_DURATION_MILLIS, 0L)
            val shouldSleepSleepTimerBeActive = timeLeftMillis > 0L
            _isSleepTimerActive.postValue(shouldSleepSleepTimerBeActive)
            sleepTimerTimeRemaining.value = timeLeftMillis

            if (shouldSleepSleepTimerBeActive) {
                setSleepTimerTitle(
                    FormattableString.ResourceString(
                        stringRes = R.string.sleep_timer_active_title,
                        placeHolderStrings = listOf(sleepTimerTimeRemainingString.value ?: "<Error>")
                    )
                )
            } else {
                setSleepTimerTitle(FormattableString.from(R.string.sleep_timer))
            }
        }
    }

    private fun setSleepTimerTitle(formattableString: FormattableString) {
        _sleepTimerChooserState.postValue(
            _sleepTimerChooserState.value?.copy(title = formattableString) ?: EMPTY_BOTTOM_CHOOSER
        )
    }

    override fun onCleared() {
        mediaServiceConnection.nowPlaying.removeObserver(playbackObserver)
        prefsRepo.unregisterPrefsListener(prefsChangeListener)
        plexConfig.isConnected.removeObserver(networkObserver)
        super.onCleared()
    }

    fun seekTo(percentProgress: Double) {
        val id: String = (audiobookId.value ?: TRACK_NOT_FOUND).toString()
        if (currentChapter.value == EMPTY_CHAPTER) {
            // Seeking by track length
            currentTrack.value?.let { curr ->
                val extras = Bundle().apply {
                    putLong(KEY_SEEK_TO_TRACK_WITH_ID, curr.id.toLong())
                }
                mediaServiceConnection.transportControls?.playFromMediaId(id, extras)
            }
        } else {
            // Seeking by chapter length
            currentChapter.value?.let { chapter ->
                // seek relative to start of current track
                val chapterDuration = chapter.endTimeOffset - chapter.startTimeOffset
                val offset = chapter.startTimeOffset + (percentProgress * chapterDuration).toLong()
                mediaServiceConnection.transportControls?.seekTo(offset)
            }
        }
    }
}