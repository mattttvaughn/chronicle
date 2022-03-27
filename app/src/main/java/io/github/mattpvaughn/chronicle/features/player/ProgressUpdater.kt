package io.github.mattpvaughn.chronicle.features.player

import android.os.Handler
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.work.*
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import io.github.mattpvaughn.chronicle.data.local.IBookRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository.Companion.TRACK_NOT_FOUND
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import io.github.mattpvaughn.chronicle.data.model.*
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack.Companion.EMPTY_TRACK
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexSyncScrobbleWorker
import io.github.mattpvaughn.chronicle.data.sources.plex.model.getDuration
import io.github.mattpvaughn.chronicle.features.currentlyplaying.CurrentlyPlaying
import io.github.mattpvaughn.chronicle.features.player.ProgressUpdater.Companion.BOOK_FINISHED_END_OFFSET_MILLIS
import io.github.mattpvaughn.chronicle.features.player.ProgressUpdater.Companion.NETWORK_CALL_FREQUENCY
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.time.minutes

/**
 * Responsible for updating playback progress of the current book/track to the local DB and to the
 * server at regular intervals while a [MediaControllerCompat] indicates that playback is active
 */
interface ProgressUpdater {
    /** Begin regularly updating the local DB and remote servers while playback is active */
    fun startRegularProgressUpdates()

    /**
     * Updates local DBs to reflect the track/book progress passed in via [progress] for a track
     * with id [trackId] and a book containing that track.
     *
     * Updates book/track progress in remote DB if [forceNetworkUpdate] == true or every
     * [NETWORK_CALL_FREQUENCY] calls. Pass additional [playbackState] for [PlexSyncScrobbleWorker]
     * to pass playback state to server
     */
    fun updateProgress(
        trackId: Int,
        playbackState: String,
        progress: Long,
        forceNetworkUpdate: Boolean
    )

    /** Update progress without providing any parameters */
    fun updateProgressWithoutParameters()

    /** Cancels regular progress updates */
    fun cancel()

    companion object {
        val BOOK_FINISHED_END_OFFSET_MILLIS = 2.minutes.toLongMilliseconds()

        /**
         * The frequency which the remote server is updated at: once for every [NETWORK_CALL_FREQUENCY]
         * calls to the local database
         */
        const val NETWORK_CALL_FREQUENCY = 10
    }
}

class SimpleProgressUpdater @Inject constructor(
    private val serviceScope: CoroutineScope,
    private val trackRepository: ITrackRepository,
    private val bookRepository: IBookRepository,
    private val workManager: WorkManager,
    private val prefsRepo: PrefsRepo,
    private val currentlyPlaying: CurrentlyPlaying
) : ProgressUpdater {

    var mediaController: MediaControllerCompat? = null

    var mediaSessionConnector: MediaSessionConnector? = null

    var player: Player? = null

    /** Frequency of progress updates */
    private val updateProgressFrequencyMs = 1000L

    /** Tracks the number of times [updateLocalProgress] has been called this session */
    private var tickCounter = 0L

    private val handler = Handler()
    private val updateProgressAction = { startRegularProgressUpdates() }

    /**
     * Updates the current track/audiobook progress in the local db and remote server.
     *
     * If we are within 2 minutes of the end of the book and playback stops, mark the book as
     * "finished" by updating the first track to progress=0 and setting it as the most recently viewed
     */
    override fun startRegularProgressUpdates() {
        requireNotNull(mediaController).let { controller ->
            if (controller.playbackState?.isPlaying != false) {
                serviceScope.launch(context = serviceScope.coroutineContext + Dispatchers.IO) {
                    updateProgress(
                        controller.metadata?.id?.toInt() ?: TRACK_NOT_FOUND,
                        MediaPlayerService.PLEX_STATE_PLAYING,
                        controller.playbackState?.currentPlayBackPosition ?: 0L,
                        false
                    )
                }
            }
        }
        handler.postDelayed(updateProgressAction, updateProgressFrequencyMs)
    }

    override fun updateProgressWithoutParameters() {
        val controller = mediaController ?: return
        val playbackState = when (controller.playbackState.state) {
            PlaybackStateCompat.STATE_PLAYING -> MediaPlayerService.PLEX_STATE_PLAYING
            PlaybackStateCompat.STATE_PAUSED -> MediaPlayerService.PLEX_STATE_PAUSED
            PlaybackStateCompat.STATE_STOPPED -> MediaPlayerService.PLEX_STATE_PAUSED
            else -> ""
        }
        val currentTrack = controller.metadata.id?.toInt() ?: return
        val currentTrackProgress = controller.playbackState.currentPlayBackPosition
        updateProgress(
            currentTrack,
            playbackState,
            currentTrackProgress,
            false
        )
    }

    override fun updateProgress(
        trackId: Int,
        playbackState: String,
        trackProgress: Long,
        forceNetworkUpdate: Boolean
    ) {
        Timber.i("Updating progress")
        if (trackId == TRACK_NOT_FOUND) {
            return
        }
        val currentTime = System.currentTimeMillis()
        serviceScope.launch(context = serviceScope.coroutineContext + Dispatchers.IO) {

            val bookId: Int = trackRepository.getBookIdForTrack(trackId)
            val track: MediaItemTrack = trackRepository.getTrackAsync(trackId) ?: EMPTY_TRACK

            // No reason to update if the track or book doesn't exist in the DB
            if (trackId == TRACK_NOT_FOUND || bookId == NO_AUDIOBOOK_FOUND_ID) {
                return@launch
            }

            val tracks = trackRepository.getTracksForAudiobookAsync(bookId)
            val book = bookRepository.getAudiobookAsync(bookId)
            val bookProgress = tracks.getTrackStartTime(track) + trackProgress
            val bookDuration = tracks.getDuration()

            currentlyPlaying.update(
                book = book ?: EMPTY_AUDIOBOOK,
                track = tracks.getActiveTrack(),
                tracks = tracks,
            )

            // Update local DB
            if (!prefsRepo.debugOnlyDisableLocalProgressTracking) {
                updateLocalProgress(
                    bookId = bookId,
                    currentTime = currentTime,
                    trackProgress = trackProgress,
                    trackId = trackId,
                    bookProgress = bookProgress,
                    tracks = tracks,
                    bookDuration = bookDuration,
                )
            }

            // Update server once every [networkCallFrequency] calls, or when manual updates
            // have been specifically requested
            if (forceNetworkUpdate || tickCounter % NETWORK_CALL_FREQUENCY == 0L) {
                updateNetworkProgress(
                    trackId,
                    playbackState,
                    trackProgress,
                    bookProgress
                )
            }
        }
    }

    private fun updateNetworkProgress(
        trackId: Int,
        playbackState: String,
        trackProgress: Long,
        bookProgress: Long
    ) {
        val syncWorkerConstraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val inputData = PlexSyncScrobbleWorker.makeWorkerData(
            trackId = trackId,
            playbackState = playbackState,
            trackProgress = trackProgress,
            bookProgress = bookProgress
        )
        val worker = OneTimeWorkRequestBuilder<PlexSyncScrobbleWorker>()
            .setInputData(inputData)
            .setConstraints(syncWorkerConstraints)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                OneTimeWorkRequest.DEFAULT_BACKOFF_DELAY_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        workManager
            .beginUniqueWork(trackId.toString(), ExistingWorkPolicy.REPLACE, worker)
            .enqueue()
    }

    private suspend fun updateLocalProgress(
        bookId: Int,
        currentTime: Long,
        trackProgress: Long,
        trackId: Int,
        bookProgress: Long,
        tracks: List<MediaItemTrack>,
        bookDuration: Long
    ) {
        tickCounter++
        bookRepository.updateProgress(bookId, currentTime, trackProgress)
        trackRepository.updateTrackProgress(trackProgress, trackId, currentTime)
        bookRepository.updateTrackData(
            bookId,
            bookProgress,
            tracks.getDuration(),
            tracks.size
        )
        if (bookDuration - bookProgress <= BOOK_FINISHED_END_OFFSET_MILLIS) {
            Timber.i("Marking $bookId as finished")
            bookRepository.setWatched(bookId)
        }
    }

    override fun cancel() {
        handler.removeCallbacks(updateProgressAction)
    }

}
