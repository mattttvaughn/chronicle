package io.github.mattpvaughn.chronicle.features.player

import android.os.Handler
import android.support.v4.media.session.MediaControllerCompat
import androidx.lifecycle.Observer
import androidx.work.*
import io.github.mattpvaughn.chronicle.application.SECONDS_PER_MINUTE
import io.github.mattpvaughn.chronicle.data.plex.PlexSyncScrobbleWorker
import io.github.mattpvaughn.chronicle.data.local.IBookRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository.Companion.TRACK_NOT_FOUND
import io.github.mattpvaughn.chronicle.data.model.getProgress
import io.github.mattpvaughn.chronicle.data.plex.model.getDuration
import io.github.mattpvaughn.chronicle.features.settings.PrefsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
    fun updateProgress(trackId: Int, state: String, progress: Long)

    /** Cancels regular progress updates */
    fun cancel()
}

class SimpleProgressUpdater @Inject constructor(
    private val serviceScope: CoroutineScope,
    private val trackRepository: ITrackRepository,
    private val bookRepository: IBookRepository,
    private val workManager: WorkManager,
    private val mediaServiceConnection: MediaServiceConnection? = null,
    inputMediaController: MediaControllerCompat? = null,
    private val prefsRepo: PrefsRepo
) : ProgressUpdater {

    private var mediaController: MediaControllerCompat? = null

    /** Frequency of progress updates */
    private val updateProgressFrequencyMs = 1000L
    private var tickCounter = 0L

    private val connectionObserver = Observer<Boolean> {
        if (it) {
            if (mediaServiceConnection != null) {
                mediaController = mediaServiceConnection.mediaController
            }
        }
    }

    init {
        require((inputMediaController != null) || (mediaServiceConnection != null))
        if (inputMediaController != null) {
            mediaController = inputMediaController
        }
        mediaServiceConnection?.isConnected?.observeForever(connectionObserver)
    }

   /**
     * Updates the current track/audiobook progress in the local db and remote server.
     *
     * If we are within 2 minutes of the end of the book and playback stops, mark the book as
     * "finished" by updating the first track to progress=0 and setting it as the most recently viewed
     */
    private val bookFinishedWindow = 2000L * SECONDS_PER_MINUTE
    private val handler = Handler()
    private val updateProgressAction = { startRegularProgressUpdates() }

    override fun startRegularProgressUpdates() {
        if (mediaController?.playbackState?.isPlaying != false) {
            serviceScope.launch {
                updateProgress(
                    mediaController?.metadata?.id?.toInt() ?: TRACK_NOT_FOUND,
                    MediaPlayerService.PLEX_STATE_PLAYING,
                    mediaController?.playbackState?.currentPlayBackPosition ?: 0L
                )
            }
        }
        handler.postDelayed(updateProgressAction, updateProgressFrequencyMs)
    }

    override fun updateProgress(trackId: Int, state: String, progress: Long) {
        tickCounter++
        if (trackId == TRACK_NOT_FOUND) {
            throw IllegalArgumentException("trackId invalid. Metadata was probably null when updateProgress was called")
        }
        val currentTime = System.currentTimeMillis()
        serviceScope.launch {
            val bookId : Int= trackRepository.getBookIdForTrack(trackId)
            val tracks = trackRepository.getTracksForAudiobookAsync(bookId)
            val isBookAlmostEnded = tracks.getDuration() - tracks.getProgress() < bookFinishedWindow
            val isBookFinished = isBookAlmostEnded && (state == MediaPlayerService.PLEX_STATE_STOPPED || state == MediaPlayerService.PLEX_STATE_PAUSED)
            val trackToUpdate: Int = if (isBookFinished) tracks.first().id else trackId
            val updatedProgress : Long = if (isBookFinished) 0L else progress

            // Update local DB
            if (!prefsRepo.debugOnlyDisableLocalProgressTracking) {
                bookRepository.updateLastViewedAt(bookId, currentTime)
                trackRepository.updateTrackProgress(
                    trackProgress = updatedProgress,
                    trackId = trackToUpdate,
                    lastViewedAt = currentTime
                )
            }

            // Update server on every fifth update to local DB (every 5 seconds by default, then)
            if (tickCounter % 5 == 0L) {
                val syncWorkerConstraints =
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                val inputData = PlexSyncScrobbleWorker.makeWorkerData(trackToUpdate, state, updatedProgress, currentTime)
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
        mediaServiceConnection?.isConnected?.removeObserver(connectionObserver)
    }

}
