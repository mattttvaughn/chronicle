package io.github.mattpvaughn.chronicle.features.player

import android.os.Bundle
import android.os.ResultReceiver
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.data.local.IBookRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import io.github.mattpvaughn.chronicle.data.model.getActiveTrack
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME
import io.github.mattpvaughn.chronicle.data.plex.PlexPrefsRepo
import io.github.mattpvaughn.chronicle.data.plex.getMediaItemUri
import io.github.mattpvaughn.chronicle.data.plex.model.getDuration
import io.github.mattpvaughn.chronicle.features.player.MediaPlayerService.Companion.KEY_PLAY_STARTING_WITH_TRACK_ID
import io.github.mattpvaughn.chronicle.injection.scopes.ServiceScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

class AudiobookMediaSessionCallback @Inject constructor(
    private val plexPrefsRepo: PlexPrefsRepo,
    private val prefsRepo: PrefsRepo,
    private val mediaController: MediaControllerCompat,
    private val exoPlayer: SimpleExoPlayer,
    private val dataSourceFactory: DefaultDataSourceFactory,
    private val trackRepository: ITrackRepository,
    private val bookRepository: IBookRepository,
    private val serviceScope: CoroutineScope,
    private val trackListStateManager: TrackListStateManager
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

    override fun onCommand(command: String?, extras: Bundle?, cb: ResultReceiver?) {
        Log.i(APP_NAME, "Command? $command")
        super.onCommand(command, extras, cb)
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
        Log.i(APP_NAME, "Seeking!")
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
        Log.i(APP_NAME, "Playing media from ID!")
        prepareBook(bookId, extras ?: Bundle(), true)
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

        Log.i(APP_NAME, "Is service scope active? ${serviceScope.isActive}")
        serviceScope.launch {
            Log.i(APP_NAME, "Starting playback in the background")
            val tracks = trackRepository.getTracksForAudiobookAsync(bookId.toInt())
            if (tracks.isNullOrEmpty()) {
                Log.i(APP_NAME, "No known tracks for book: $bookId, attempting to fetch them")
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
                Log.i(APP_NAME, "Starting playback for $bookId: $tracks")
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
                exoPlayer.seekTo(tracks.indexOf(trackToPlay), progress)

                // Inform plex server that audio playback session has started
                val serverId = plexPrefsRepo.getServer()?.serverId
                if (serverId != null) {
                    try {
                        Injector.get().plexMediaService().startMediaSession(
                            getMediaItemUri(serverId, bookId)
                        )
                    } catch (e: Error) {
                        Log.e(APP_NAME, "Failed to start media session: $e")
                    }
                } else {
                    Log.w(
                        APP_NAME,
                        "Unknown server id. Cannot start active session. Media playback may not be saved"
                    )
                }

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

    /**
     * Shadows the state of tracks in the queue in order to calculate seeks for
     * [AudiobookMediaSessionCallback]
     */
    @ServiceScope
    class TrackListStateManager @Inject constructor() {
        /** The list of [MediaItemTrack]s currently playing */
        var trackList = emptyList<MediaItemTrack>()

        /** The index of the current track within [trackList] */
        var currentTrackIndex: Int = 0

        /** The number of milliseconds between current position and start of track */
        var currentPosition: Long = 0

        /** Return the duration of a track at index [trackIndex] in milliseconds */
        private fun getTrackDuration(trackIndex: Int): Long {
            return trackList[trackIndex].duration
        }

        private fun currentTrackDuration(): Long {
            return getTrackDuration(currentTrackIndex)
        }

        fun hasNext(): Boolean {
            return currentTrackIndex < trackList.size
        }

        fun hasPrevious(): Boolean {
            return currentTrackIndex > 0
        }

        /** Seeks forwards or backwards in the playlist by [offset] millis*/
        fun seekByRelative(offset: Long) {
            if (offset > 0) {
                seekForwards(offset)
            } else {
                seekBackwards(abs(offset))
            }
        }

        /** Seek backwards by [offset] ms */
        private fun seekBackwards(offset: Long) {
            check(offset > 0)
            if (currentPosition > offset) {
                /** Skip backwards in current track by [offset] */
                currentPosition -= offset
            } else {
                /**
                 * Skip a combined [offset] milliseconds backwards between this track and previous
                 * one
                 */
                val amountSkippedFromFirstTrack = currentPosition
                currentPosition = if (hasPrevious()) {
                    currentTrackIndex--
                    currentTrackDuration() - (offset - amountSkippedFromFirstTrack)
                } else {
                    0
                }
            }
        }

        private fun seekForwards(offset: Long) {
            if (currentPosition + offset <= currentTrackDuration()) {
                // Skip 30s forward in current track
                currentPosition += offset
            } else {
                // Skip a combined 30s forward b/w this track and next one
                val amountSkippedFromFirstTrack = currentTrackDuration() - currentPosition
                currentPosition = if (hasNext()) {
                    currentTrackIndex++
                    offset - amountSkippedFromFirstTrack
                } else {
                    currentTrackDuration()
                }
            }
        }
    }
}

