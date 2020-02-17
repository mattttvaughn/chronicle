package io.github.mattpvaughn.chronicle.features.player

import android.os.Bundle
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import io.github.mattpvaughn.chronicle.data.local.IBookRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository
import io.github.mattpvaughn.chronicle.data.model.getActiveTrack
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME
import io.github.mattpvaughn.chronicle.data.plex.PlexMediaApi
import io.github.mattpvaughn.chronicle.data.plex.PlexPrefsRepo
import io.github.mattpvaughn.chronicle.data.plex.getMediaItemUri
import io.github.mattpvaughn.chronicle.data.plex.model.getDuration
import io.github.mattpvaughn.chronicle.features.player.MediaPlayerService.Companion.KEY_PLAY_STARTING_WITH_TRACK_ID
import io.github.mattpvaughn.chronicle.features.settings.PrefsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AudiobookMediaSessionCallback(
    private val plexPrefsRepo: PlexPrefsRepo,
    private val prefsRepo: PrefsRepo,
    private val mediaController: MediaControllerCompat,
    private val exoPlayer: SimpleExoPlayer,
    private val dataSourceFactory: DefaultDataSourceFactory,
    private val trackRepository: ITrackRepository,
    private val bookRepository: IBookRepository,
    private val sleepTimer: SleepTimer,
    private val serviceScope: CoroutineScope,
    private val trackListStateManager: MediaPlayerService.TrackListStateManager
) : MediaSessionCompat.Callback() {

    companion object {
        const val NO_LONG_FOUND = -23323L
    }
    override fun onSkipToNext() {
        onCustomAction(SKIP_FORWARDS_STRING, null)
        super.onSkipToNext()
    }

    override fun onPrepareFromSearch(query: String?, extras: Bundle?) {
        onPlayFromSearch(query, extras)
        super.onPrepareFromSearch(query, extras)
    }

    override fun onPlayFromSearch(query: String?, extras: Bundle?) {
        if (query.isNullOrEmpty()) {
            return
        }
        serviceScope.launch {
            val matchingBooks = bookRepository.searchAsync(query)
            if (matchingBooks.isNotEmpty()) {
                onPlayFromMediaId(matchingBooks.first().id.toString(), null)
            }
        }
        super.onPlayFromSearch(query, extras)
    }

    override fun onSkipToPrevious() {
        onCustomAction(SKIP_BACKWARDS_STRING, null)
        super.onSkipToPrevious()
    }

    override fun onCustomAction(action: String?, extras: Bundle?) {
        when (action) {
            SKIP_FORWARDS_STRING -> {
                skipForwards()
            }
            SKIP_BACKWARDS_STRING -> {
                skipBackwards()
            }
            START_SLEEP_TIMER_STRING -> {
                if (extras == null) {
                    throw IllegalArgumentException("Sleep timer requires a duration pass to custom action corresponding to KEY_SLEEP_TIMER_DURATION!")
                }
                val durationMillis = extras.getLong(KEY_SLEEP_TIMER_DURATION_MILLIS, NO_LONG_FOUND)
                check(durationMillis != NO_LONG_FOUND)
                sleepTimer.update(durationMillis)
                sleepTimer.start()
            }
            SLEEP_TIMER_ACTION_EXTEND -> {
                if (extras == null) {
                    throw IllegalArgumentException("Sleep timer requires a duration pass to custom action corresponding to KEY_SLEEP_TIMER_DURATION!")
                }
                val extensionDurationMillis = extras.getLong(KEY_SLEEP_TIMER_DURATION_MILLIS, NO_LONG_FOUND)
                check(extensionDurationMillis != NO_LONG_FOUND)
                sleepTimer.extend(extensionDurationMillis)
            }
            SLEEP_TIMER_ACTION_CANCEL -> {
                sleepTimer.cancel()
            }
            else -> throw IllegalStateException("Unknown custom media action: $action")
        }
        super.onCustomAction(action, extras)
    }

    override fun onPause() {
        super.onPause()
        Log.i(APP_NAME, "Pause button received!")
        if (!mediaController.playbackState.isPrepared) {
            Log.i(APP_NAME, "Started from dead")
            resumePlayFromEmpty()
        }
        exoPlayer.playWhenReady = false
    }

    override fun onPlay() {
        super.onPlay()
        // Check if session is inactive. If so, we are resuming a session right here, so play
        // the most recently played book
        Log.i(APP_NAME, "Play button received!")
        if (!mediaController.playbackState.isPrepared) {
            Log.i(APP_NAME, "Started from dead")
            resumePlayFromEmpty()
        }
        exoPlayer.playWhenReady = true
    }

    override fun onSeekTo(pos: Long) {
        super.onSeekTo(pos)
        exoPlayer.seekTo(pos)
    }

    /**
     * Assume mediaId refers to the audiobook as a whole, and there is an option string in the
     * extras bundle referring to the specific track at key [KEY_PLAY_STARTING_WITH_TRACK_ID]
     */
    override fun onPlayFromMediaId(bookId: String?, extras: Bundle?) {
        if (bookId.isNullOrEmpty()) {
            throw IllegalArgumentException("MediaControllerCallback.onPlayFromMediaId() must be passed a Book ID. Received bookId = $bookId")
        }

        prepareBook(bookId, extras ?: Bundle(), true)

        // Inform plex server that audio playback session has started
        val serverId = plexPrefsRepo.getServer()?.serverId
        if (serverId != null) {
            try {
                PlexMediaApi.retrofitService.startMediaSession(getMediaItemUri(serverId, bookId))
            } catch (e: Error) {
                Log.e(APP_NAME, "Failed to start media session: $e")
            }
        } else {
            Log.w(
                APP_NAME,
                "Unknown service ID. Cannot start active session. Media playback may not be saved"
            )
        }

    }

    override fun onPrepareFromMediaId(bookId: String?, extras: Bundle?) {
        if (bookId.isNullOrEmpty()) {
            throw IllegalArgumentException("MediaControllerCallback.onPrepareFromMediaId() must be passed a Book ID. Received bookId = $bookId")
        }
        prepareBook(bookId, extras ?: Bundle(), false)
    }

    private fun prepareBook(bookId: String, extras: Bundle, playWhenReady: Boolean) {
        val startingTrackOverrideKey =
            extras.getInt(KEY_PLAY_STARTING_WITH_TRACK_ID, MediaPlayerService.ACTIVE_TRACK)
        val file = prefsRepo.cachedMediaDir

        serviceScope.launch {
            val tracks = trackRepository.getTracksForAudiobookAsync(bookId.toInt())
            if (tracks.isNullOrEmpty()) {
                // Tracks haven't been loaded by UI for this track, so load it here
                serviceScope.launch {
                    val networkTracks = trackRepository.loadTracksForAudiobook(bookId.toInt())
                    bookRepository.updateTrackData(
                        bookId.toInt(),
                        networkTracks.getDuration(),
                        networkTracks.size
                    )
                    // TODO: this may loop forever if no network is available
                    prepareBook(bookId, Bundle(), true)
                }
            } else {
                trackListStateManager.trackList = tracks
                val metadataList = buildPlaylist(tracks, file)
                val mediaSource = metadataList.toMediaSource(dataSourceFactory)

                val trackToPlay = if (startingTrackOverrideKey == MediaPlayerService.ACTIVE_TRACK) {
                    tracks.getActiveTrack()
                } else {
                    tracks.find { it.id == startingTrackOverrideKey } ?: tracks.getActiveTrack()
                }

                val progress =
                    if (startingTrackOverrideKey == MediaPlayerService.ACTIVE_TRACK) trackToPlay.progress else 0

                trackListStateManager.currentTrackIndex = tracks.indexOf(trackToPlay)
                trackListStateManager.currentPosition = progress

                exoPlayer.playWhenReady = playWhenReady
                exoPlayer.prepare(mediaSource)
                // Note: seek to (index - 1) because [_tracks] is 1-indexed
                exoPlayer.seekTo(trackToPlay.index - 1, progress)
            }
        }
    }

    /**
     * Check if session is inactive. If so, we are resuming a session right here, so play
     * the most recently played book. Unsure why onPause() would be called instead of
     * onPlay(), but either MediaButtonService or my bluetooth headphones send an resume
     * sessions an onPause() keyevent sometimes
     */
    private fun resumePlayFromEmpty() {
        // Find most recent book
        serviceScope.launch {
            val mostRecentBook = bookRepository.getMostRecentlyPlayed()
            mediaController.transportControls.playFromMediaId(mostRecentBook.id.toString(), null)
        }
    }

    private fun skipForwards() {
        if (!exoPlayer.isLoading) {
            // Update [trackListStateManager] to reflect the current playback state
            trackListStateManager.currentTrackIndex = exoPlayer.currentWindowIndex
            trackListStateManager.currentPosition = exoPlayer.currentPosition
        }
        trackListStateManager.seekByRelative(SKIP_FORWARDS_DURATION_MS)
        exoPlayer.seekTo(
            trackListStateManager.currentTrackIndex,
            trackListStateManager.currentPosition
        )
    }

    private fun skipBackwards() {
        if (!exoPlayer.isLoading) {
            // Update [trackListStateManager] to reflect the current playback state
            trackListStateManager.currentTrackIndex = exoPlayer.currentWindowIndex
            trackListStateManager.currentPosition = exoPlayer.currentPosition
        }
        trackListStateManager.seekByRelative(SKIP_BACKWARDS_DURATION_MS)
        exoPlayer.seekTo(
            trackListStateManager.currentTrackIndex,
            trackListStateManager.currentPosition
        )
    }
}

