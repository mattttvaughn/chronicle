package io.github.mattpvaughn.chronicle.features.player

import android.os.Handler
import android.support.v4.media.session.MediaControllerCompat
import androidx.work.*
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import io.github.mattpvaughn.chronicle.application.MILLIS_PER_SECOND
import io.github.mattpvaughn.chronicle.application.SECONDS_PER_MINUTE
import io.github.mattpvaughn.chronicle.data.local.IBookRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository.Companion.TRACK_NOT_FOUND
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack.Companion.EMPTY_TRACK
import io.github.mattpvaughn.chronicle.data.model.getTrackStartTime
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexSyncScrobbleWorker
import io.github.mattpvaughn.chronicle.data.sources.plex.model.getDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Responsible for updating playback progress of the current book/track to the local DB and to the
 * server at regular intervals while a [MediaControllerCompat] indicates that playback is active
 */
interface ProgressUpdater {
    /** Begin regularly updating the local DB and remote servers while playback is active */
    fun startRegularProgressUpdates()

    /**
     * Updates the progress of a track (and book corresponding to that track) on the local DB and
     * at the remote server
     */
    fun updateProgress(
        trackId: Int,
        playbackState: String,
        trackProgress: Long,
        manualUpdate: Boolean
    )

    /** Cancels regular progress updates */
    fun cancel()

    companion object {
        const val BOOK_FINISHED_END_OFFSET_MILLIS = 2L * MILLIS_PER_SECOND * SECONDS_PER_MINUTE
    }
}

class SimpleProgressUpdater @Inject constructor(
    private val serviceScope: CoroutineScope,
    private val trackRepository: ITrackRepository,
    private val bookRepository: IBookRepository,
    private val workManager: WorkManager,
    private val prefsRepo: PrefsRepo
) : ProgressUpdater {

    var mediaController: MediaControllerCompat? = null

    var mediaSessionConnector: MediaSessionConnector? = null

    var player: Player? = null

    /** Frequency of progress updates */
    private val updateProgressFrequencyMs = 1000L
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

    override fun updateProgress(
        trackId: Int,
        playbackState: String,
        trackProgress: Long,
        manualUpdate: Boolean
    ) {
        Timber.i("Updating progress")
        if (!manualUpdate) {
            tickCounter++
        }
        if (trackId == TRACK_NOT_FOUND) {
            return
        }
        // TODO: bring back if we remove MediaSessionConnector
//        mediaSessionConnector?.invalidateMediaSessionMetadata()
//        mediaSessionConnector?.invalidateMediaSessionPlaybackState()
        val currentTime = System.currentTimeMillis()
        serviceScope.launch(context = serviceScope.coroutineContext + Dispatchers.IO) {
            val bookId: Int = trackRepository.getBookIdForTrack(trackId)
            val tracks = trackRepository.getTracksForAudiobookAsync(bookId)

            val track: MediaItemTrack = trackRepository.getTrackAsync(trackId) ?: EMPTY_TRACK
            check(trackId != EMPTY_TRACK.id)
            val bookProgress = tracks.getTrackStartTime(track) + trackProgress

            // Update local DB
            if (!prefsRepo.debugOnlyDisableLocalProgressTracking) {
                bookRepository.updateProgress(bookId, currentTime, trackProgress)
                trackRepository.updateTrackProgress(trackProgress, trackId, currentTime)
                bookRepository.updateTrackData(
                    bookId,
                    bookProgress,
                    tracks.getDuration(),
                    tracks.size
                )
            }

            // Update server on every fifth update to local DB (every 5 seconds by default), or on
            // manual progress updates
            if (manualUpdate || tickCounter % 5 == 0L) {
                val syncWorkerConstraints =
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                val inputData = PlexSyncScrobbleWorker.makeWorkerData(
                    trackId = trackId,
                    playbackState = playbackState,
                    trackProgress = trackProgress,
                    playbackTimeStamp = currentTime,
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
        }
    }

    override fun cancel() {
        handler.removeCallbacks(updateProgressAction)
    }

}
