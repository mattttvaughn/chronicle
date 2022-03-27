package io.github.mattpvaughn.chronicle.features.player

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.text.format.DateUtils
import android.view.KeyEvent
import android.view.KeyEvent.*
import androidx.lifecycle.Observer
import com.github.michaelbull.result.Ok
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import io.github.mattpvaughn.chronicle.BuildConfig
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.application.MILLIS_PER_SECOND
import io.github.mattpvaughn.chronicle.data.local.IBookRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import io.github.mattpvaughn.chronicle.data.model.*
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexConfig
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexPrefsRepo
import io.github.mattpvaughn.chronicle.data.sources.plex.getMediaItemUri
import io.github.mattpvaughn.chronicle.data.sources.plex.model.getDuration
import io.github.mattpvaughn.chronicle.features.currentlyplaying.CurrentlyPlaying
import io.github.mattpvaughn.chronicle.features.player.MediaPlayerService.Companion.ACTIVE_TRACK
import io.github.mattpvaughn.chronicle.features.player.MediaPlayerService.Companion.KEY_SEEK_TO_TRACK_WITH_ID
import io.github.mattpvaughn.chronicle.features.player.MediaPlayerService.Companion.KEY_START_TIME_TRACK_OFFSET
import io.github.mattpvaughn.chronicle.features.player.MediaPlayerService.Companion.USE_SAVED_TRACK_PROGRESS
import io.github.mattpvaughn.chronicle.injection.scopes.ServiceScope
import kotlinx.coroutines.*
import timber.log.Timber
import javax.inject.Inject

@ExperimentalCoroutinesApi
@ServiceScope
class AudiobookMediaSessionCallback @Inject constructor(
    private val plexPrefsRepo: PlexPrefsRepo,
    private val prefsRepo: PrefsRepo,
    private val plexConfig: PlexConfig,
    private val mediaController: MediaControllerCompat,
    private val dataSourceFactory: DefaultHttpDataSourceFactory,
    private val trackRepository: ITrackRepository,
    private val bookRepository: IBookRepository,
    private val serviceScope: CoroutineScope,
    private val trackListStateManager: TrackListStateManager,
    private val foregroundServiceController: ForegroundServiceController,
    private val serviceController: ServiceController,
    private val mediaSessionConnector: MediaSessionConnector,
    private val mediaSession: MediaSessionCompat,
    private val appContext: Context,
    private val currentlyPlaying: CurrentlyPlaying,
    private val progressUpdater: ProgressUpdater,
    defaultPlayer: SimpleExoPlayer
) : MediaSessionCompat.Callback() {

    // Default to ExoPlayer to prevent having a nullable field
    var currentPlayer: Player = defaultPlayer

    companion object {
        const val ACTION_SEEK = "seek"
        const val OFFSET_MS = "offset"
    }

    override fun onPrepareFromSearch(query: String?, extras: Bundle?) {
        Timber.i("Prepare from search!")
        handleSearch(query, false)
    }

    override fun onPlayFromSearch(query: String?, extras: Bundle?) {
        Timber.i("Play from search!")
        handleSearch(query, true)
    }

    private fun handleSearch(query: String?, playWhenReady: Boolean) {
        if (query.isNullOrEmpty()) {
            // take most recently played book, start that
            serviceScope.launch(Injector.get().unhandledExceptionHandler()) {
                val mostRecentlyPlayed = bookRepository.getMostRecentlyPlayed()
                val bookToPlay = if (mostRecentlyPlayed == EMPTY_AUDIOBOOK) {
                    bookRepository.getRandomBookAsync()
                } else {
                    mostRecentlyPlayed
                }
                if (playWhenReady) {
                    onPlayFromMediaId(bookToPlay.id.toString(), null)
                } else {
                    onPrepareFromMediaId(bookToPlay.id.toString(), null)
                }
            }
            return
        }
        serviceScope.launch(Injector.get().unhandledExceptionHandler()) {
            val matchingBooks = bookRepository.searchAsync(query)
            if (matchingBooks.isNotEmpty()) {
                val result = matchingBooks.first().id.toString()
                if (playWhenReady) {
                    onPlayFromMediaId(result, null)
                } else {
                    onPrepareFromMediaId(result, null)
                }
            }
        }
    }

    override fun onPause() {
        if (!mediaController.playbackState.isPrepared) {
            Timber.i("Started from dead")
            resumePlayFromEmpty(false)
        } else {
            currentPlayer.playWhenReady = false
        }
    }


    private fun skipToNext() {
        currentPlayer.skipToNext(trackListStateManager, currentlyPlaying, progressUpdater)
    }

    private fun skipToPrevious() {
        currentPlayer.skipToPrevious(trackListStateManager, currentlyPlaying, progressUpdater)
    }

    private fun skipForwards() {
        Timber.i("Track manager is $trackListStateManager")
        currentPlayer.seekRelative(trackListStateManager, prefsRepo.jumpForwardSeconds * MILLIS_PER_SECOND)
    }

    private fun skipBackwards() {
        Timber.i("Track manager is $trackListStateManager")
        currentPlayer.seekRelative(trackListStateManager, prefsRepo.jumpBackwardSeconds * MILLIS_PER_SECOND * -1)
    }

    private fun changeSpeed() {
        changeSpeed(trackListStateManager, mediaSessionConnector, prefsRepo, currentlyPlaying, progressUpdater)
        Timber.i("New Speed: %s", prefsRepo.playbackSpeed)
    }

    override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
        if (mediaButtonEvent == null) {
            return false
        }
        val ke: KeyEvent? = mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
        Timber.i("Media button event: $ke")
        // Media button events usually come in either an "ACTION_DOWN" + "ACTION_UP" pair, or
        // as a single ACTION_DOWN. Just take ACTION_DOWNs
        if (ke?.action == ACTION_DOWN) {
            // These really handle bluetooth media actions only, but the framework handles
            // pause/play for inline wired headphones
            return when (ke.keyCode) {
                KEYCODE_MEDIA_NEXT -> {
                    skipToNext()
                    true
                }
                KEYCODE_MEDIA_PREVIOUS -> {
                    skipToPrevious()
                    true
                }
                KEYCODE_MEDIA_SKIP_FORWARD -> {
                    skipForwards()
                    true
                }
                KEYCODE_MEDIA_SKIP_BACKWARD -> {
                    skipBackwards()
                    true
                }
                KEYCODE_MEDIA_PAUSE -> {
                    onPause()
                    true
                }
                KEYCODE_MEDIA_PLAY -> {
                    onPlay()
                    true
                }
                KEYCODE_MEDIA_STOP -> {
                    onStop()
                    true
                }
                else -> false
            }
        }
        return false
    }

    override fun onPlay() {
        // Check if session is inactive. If so, we are resuming a session right here, so play
        // the most recently played book
        if (!mediaController.playbackState.isPrepared) {
            Timber.i("Started from dead")
            resumePlayFromEmpty(true)
        } else {
            currentPlayer.playWhenReady = true
        }
    }

    override fun onSeekTo(pos: Long) {
        Timber.i("Seeking to: ${DateUtils.formatElapsedTime(pos)}")
        currentPlayer.seekTo(pos)
    }

    override fun onCustomAction(action: String?, extras: Bundle?) {
        Timber.i("Custom action")
        when (action) {
            // onSeekTo doesn't appear to be called by transportControls.seekTo, so use a custom
            // action instead...
            ACTION_SEEK -> {
                val offsetMs = extras?.getLong(OFFSET_MS)
                    ?: throw IllegalStateException("Received ACTION_SEEK without an offset")
                trackListStateManager.seekByRelative(offsetMs)
                currentPlayer.seekTo(
                    trackListStateManager.currentTrackIndex,
                    trackListStateManager.currentTrackProgress
                )
            }
            SKIP_FORWARDS_STRING -> skipForwards()
            SKIP_BACKWARDS_STRING -> skipBackwards()
            CHANGE_PLAYBACK_SPEED -> changeSpeed()
            SKIP_TO_NEXT_STRING -> skipToNext()
            SKIP_TO_PREVIOUS_STRING -> skipToPrevious()
        }
    }

    /**
     * Assume mediaId refers to the audiobook as a whole, and there is an option string in the
     * extras bundle referring to the specific track at key [KEY_START_TIME_TRACK_OFFSET]
     */
    override fun onPlayFromMediaId(bookId: String?, extras: Bundle?) {
        if (bookId.isNullOrEmpty()) {
            throw IllegalArgumentException("MediaControllerCallback.onPlayFromMediaId() must be passed a Book ID. Received bookId = $bookId")
        }
        Timber.i("Playing media from ID!")
        playBook(bookId, extras ?: Bundle(), true)
    }

    override fun onPrepareFromMediaId(bookId: String?, extras: Bundle?) {
        if (bookId.isNullOrEmpty()) {
            throw IllegalArgumentException("MediaControllerCallback.onPrepareFromMediaId() must be passed a Book ID. Received bookId = $bookId")
        }
        playBook(bookId, extras ?: Bundle(), false)
    }

    private fun playBook(bookId: String, extras: Bundle, playWhenReady: Boolean) {
        // The [MediaItemTrack.id] of the track to be played, either a unique non-negative ID from
        // the DB, or default to ACTIVE_TRACK, the most recently listened track in [bookId]
        val startingTrackId = extras.getLong(KEY_SEEK_TO_TRACK_WITH_ID, ACTIVE_TRACK)

        // [startTimeOffsetMillis] is an offset in milliseconds from start of the track where
        // [MediaItemTrack.id] == [startingTrackId] from the local repo
        //
        // Default to [USE_SAVED_TRACK_PROGRESS], which uses the current progress of track where
        // [MediaItemTrack.id] == [startingTrackId] in the local repo
        val startTimeOffsetMillis =
            extras.getLong(KEY_START_TIME_TRACK_OFFSET, USE_SAVED_TRACK_PROGRESS)

        check(bookId != EMPTY_AUDIOBOOK.id.toString()) { "Attempted to play empty audiobook" }

        if (BuildConfig.DEBUG) {
            val debugTrackIdString =
                if (startingTrackId == ACTIVE_TRACK) "ACTIVE_TRACK" else startingTrackId
            val readableOffset = startTimeOffsetMillis.takeIf { it != USE_SAVED_TRACK_PROGRESS }
                ?: "USE_SAVED_TRACK_PROGRESS"
            Timber.i("Starting playback for book=$bookId track=$debugTrackIdString at offset $readableOffset")
        }
        serviceScope.launch {
            val tracks = withContext(Dispatchers.IO) {
                trackRepository.getTracksForAudiobookAsync(bookId.toInt())
            }
            if (tracks.isNullOrEmpty()) {
                handlePlayBookWithNoTracks(bookId, tracks, extras, playWhenReady)
                return@launch
            }

            Timber.i("Tracks: $tracks")

            trackListStateManager.trackList = tracks
            val metadataList = buildPlaylist(tracks, plexConfig)

            check(startingTrackId != ACTIVE_TRACK || startingTrackId.toInt() !in tracks.map { it.id }) { "Track not found! " }

            val startingTrack = if (startingTrackId == ACTIVE_TRACK) {
                tracks.getActiveTrack()
            } else {
                tracks.find { it.id == startingTrackId.toInt() }
            }

            checkNotNull(startingTrack) { "No starting track provided for $startingTrackId" }
            val startingTrackIndex = tracks.sorted().indexOf(startingTrack)
            val trueStartTimeOffsetMillis = if (startTimeOffsetMillis != USE_SAVED_TRACK_PROGRESS) {
                startTimeOffsetMillis
            } else {
                startingTrack.progress
            }
            Timber.i("Starting at index: $startingTrackIndex, offset by $trueStartTimeOffsetMillis")
            trackListStateManager.updatePosition(startingTrackIndex, trueStartTimeOffsetMillis)

            val book = withContext(Dispatchers.IO) {
                return@withContext bookRepository.getAudiobookAsync(bookId.toInt())
            }
            if (book == null || book.id == NO_AUDIOBOOK_FOUND_ID) {
                // Return if no book found- no reason to setup playback if there's no book
                return@launch
            }

            // Auto-rewind depending on last listened time for the book. Don't rewind if we're
            // starting a new chapter/track of a book
            if (startTimeOffsetMillis == USE_SAVED_TRACK_PROGRESS) {
                trackListStateManager.seekByRelative(-1L * calculateRewindDuration(book))
            } else {
                // Seek slightly forwards so previous track/chapter name isn't erroneously shown
                trackListStateManager.seekByRelative(300)
            }

            currentPlayer.playWhenReady = playWhenReady
            val player = currentPlayer

            // Refresh auth token in [dataSourceFactory] in case the server has changed without
            // the service being recreated
            val props = dataSourceFactory.defaultRequestProperties
            props.set(
                "X-Plex-Token",
                plexPrefsRepo.server?.accessToken
                    ?: plexPrefsRepo.user?.authToken
                    ?: plexPrefsRepo.accountAuthToken
            )
            val factory = DefaultDataSourceFactory(appContext, dataSourceFactory)
            when (player) {
                is ExoPlayer -> {
                    val mediaSource = metadataList.toMediaSource(plexPrefsRepo, factory)
                    player.prepare(mediaSource)
                }
                else -> throw NoWhenBranchMatchedException("Unknown media player")
            }

            currentlyPlaying.update(
                book = book,
                tracks = tracks,
                track = startingTrack
            )

            player.seekTo(
                trackListStateManager.currentTrackIndex,
                trackListStateManager.currentTrackProgress
            )

            // Inform plex server that audio playback session has started
            val serverId = plexPrefsRepo.server?.serverId
            if (serverId == null) {
                Timber.w("Unknown server id. Cannot start active session. Media playback may not be saved")
            } else {
                try {
                    Injector.get().plexMediaService().startMediaSession(
                        getMediaItemUri(serverId, bookId)
                    )
                } catch (e: Throwable) {
                    Timber.e("Failed to start media session: $e")
                }
            }
        }
    }

    private suspend fun handlePlayBookWithNoTracks(
        bookId: String,
        tracks: List<MediaItemTrack>,
        extras: Bundle,
        playWhenReady: Boolean
    ) {
        Timber.i("No known tracks for book: $bookId, attempting to fetch them")
        // Tracks haven't been loaded by UI for this track, so load it here
        val networkTracks = withContext(Dispatchers.IO) {
            trackRepository.loadTracksForAudiobook(bookId.toInt())
        }
        if (networkTracks is Ok) {
            bookRepository.updateTrackData(
                bookId.toInt(),
                networkTracks.value.getProgress(),
                networkTracks.value.getDuration(),
                networkTracks.value.size
            )
            val audiobook = bookRepository.getAudiobookAsync(bookId.toInt())
            if (audiobook != null) {
                bookRepository.syncAudiobook(audiobook, tracks)
            }
            playBook(bookId, extras, playWhenReady)
        }
    }

    private fun calculateRewindDuration(book: Audiobook?): Long {
        if (!prefsRepo.autoRewind) return 0L
        val millisSinceLastListen = System.currentTimeMillis() - (book?.lastViewedAt ?: 0L)
        val secondsSinceLastListen = millisSinceLastListen / (1000)
        return when {
            secondsSinceLastListen in 15..60 -> 5 * 1000L
            secondsSinceLastListen in 60..(60 * 60) -> 10 * 1000L
            secondsSinceLastListen > (60 * 60 * 24) -> 20 * 1000L
            else -> 0L
        }
    }

    /**
     * Resume playback, assuming that service is starting from death. We cannot assume that
     * UI exists or that the requisite setup has happened, including connecting to a server or
     * refreshing data.
     */
    private fun resumePlayFromEmpty(playWhenReady: Boolean) {
        val connectedObserver = object : Observer<Boolean> {
            override fun onChanged(isConnected: Boolean) {
                // Don't try starting playback until we've connected to a server
                if (!isConnected) {
                    return
                }

                // Only run these resume methods once after reconnecting
                plexConfig.isConnected.removeObserver(this)

                serviceScope.launch(Injector.get().unhandledExceptionHandler()) {
                    val mostRecentBook = bookRepository.getMostRecentlyPlayed()
                    if (mostRecentBook == EMPTY_AUDIOBOOK) {
                        return@launch
                    }
                    if (playWhenReady) {
                        onPlayFromMediaId(mostRecentBook.id.toString(), null)
                    } else {
                        onPrepareFromMediaId(mostRecentBook.id.toString(), null)
                    }
                }
            }
        }

        plexConfig.isConnected.observeForever(connectedObserver)
    }

    // Kill the playback service when stop() is called, so Service can be recreated when needed
    // with the correct info from global storage
    override fun onStop() {
        Timber.i("Stopping media playback")
        currentPlayer.stop()
        mediaSession.setPlaybackState(EMPTY_PLAYBACK_STATE)
        foregroundServiceController.stopForeground(true)
        serviceController.stopService()
    }
}

