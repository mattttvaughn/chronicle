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
import io.github.mattpvaughn.chronicle.data.model.NO_AUDIOBOOK_FOUND_ID
import io.github.mattpvaughn.chronicle.data.model.getTrackStartTime
import io.github.mattpvaughn.chronicle.data.sources.MediaSource.Companion.NO_SOURCE_FOUND
import io.github.mattpvaughn.chronicle.data.sources.RemoteSyncScrobbleWorker
import io.github.mattpvaughn.chronicle.data.sources.plex.model.getDuration
import io.github.mattpvaughn.chronicle.features.player.ProgressUpdater.Companion.NETWORK_CALL_FREQUENCY
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
     * Updates local DBs to reflect the track/book progress passed in via [progress] for a track
     * with id [trackId] and a book containing that track.
     *
     * Updates book/track progress in remote DB if [forceNetworkUpdate] == true or every
     * [NETWORK_CALL_FREQUENCY] calls. Pass additional [playbackState] for [RemoteSyncScrobbleWorker]
     * to pass playback state to server
     */
    fun updateProgress(
        trackId: Int,
        playbackState: String,
        progress: Long,
        forceNetworkUpdate: Boolean
    )

    /** Cancels regular progress updates */
    fun cancel()

    companion object {
        const val BOOK_FINISHED_END_OFFSET_MILLIS = 2L * MILLIS_PER_SECOND * SECONDS_PER_MINUTE

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
    private val prefsRepo: PrefsRepo
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

    override fun updateProgress(
        trackId: Int,
        playbackState: String,
        progress: Long,
        forceNetworkUpdate: Boolean
    ) {
        Timber.i("Updating progress")
        if (trackId == TRACK_NOT_FOUND) {
            return
        }
        // TODO: bring back if we remove MediaSessionConnector
//        mediaSessionConnector?.invalidateMediaSessionMetadata()
//        mediaSessionConnector?.invalidateMediaSessionPlaybackState()
        val currentTime = System.currentTimeMillis()
        serviceScope.launch(context = serviceScope.coroutineContext + Dispatchers.IO) {

            val bookId: Int = trackRepository.getBookIdForTrack(trackId)
            val track: MediaItemTrack = trackRepository.getTrackAsync(trackId) ?: EMPTY_TRACK

            // No reason to update if the track or book doesn't exist in the DB
            if (trackId == TRACK_NOT_FOUND || bookId == NO_AUDIOBOOK_FOUND_ID) {
                return@launch
            }

            val tracks = trackRepository.getTracksForAudiobookAsync(bookId)
            val bookProgress = tracks.getTrackStartTime(track) + progress

            val sourceId = bookRepository.getAudiobookAsync(bookId)?.source ?: NO_SOURCE_FOUND

            // Update local DB
            if (!prefsRepo.debugOnlyDisableLocalProgressTracking) {
                updateLocalProgress(
                    bookId,
                    currentTime,
                    progress,
                    trackId,
                    bookProgress,
                    tracks
                )
            }

            // Update server once every [networkCallFrequency] calls, or when manual updates
            // have been specifically requested
            if (forceNetworkUpdate || tickCounter % NETWORK_CALL_FREQUENCY == 0L) {
                updateNetworkProgress(
                    trackId,
                    playbackState,
                    progress,
                    currentTime,
                    bookProgress,
                    sourceId
                )
            }
        }
    }

    private fun updateNetworkProgress(
        trackId: Int,
        playbackState: String,
        trackProgress: Long,
        currentTime: Long,
        bookProgress: Long,
        sourceId: Long
    ) {
        val syncWorkerConstraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val inputData = RemoteSyncScrobbleWorker.makeWorkerData(
            trackId = trackId,
            playbackState = playbackState,
            trackProgress = trackProgress,
            playbackTimeStamp = currentTime,
            bookProgress = bookProgress,
            sourceId = sourceId
        )
        val worker = OneTimeWorkRequestBuilder<RemoteSyncScrobbleWorker>()
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
        tracks: List<MediaItemTrack>
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
    }

    override fun cancel() {
        handler.removeCallbacks(updateProgressAction)
    }

}
