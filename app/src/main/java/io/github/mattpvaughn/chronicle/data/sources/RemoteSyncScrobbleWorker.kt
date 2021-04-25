package io.github.mattpvaughn.chronicle.data.sources

import android.content.Context
import androidx.work.*
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository.Companion.TRACK_NOT_FOUND
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import io.github.mattpvaughn.chronicle.data.model.NO_AUDIOBOOK_FOUND_ID
import io.github.mattpvaughn.chronicle.data.sources.plex.model.getDuration
import io.github.mattpvaughn.chronicle.features.player.MediaPlayerService.Companion.PLEX_STATE_PAUSED
import io.github.mattpvaughn.chronicle.features.player.MediaPlayerService.Companion.PLEX_STATE_STOPPED
import io.github.mattpvaughn.chronicle.features.player.ProgressUpdater.Companion.BOOK_FINISHED_END_OFFSET_MILLIS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber


class RemoteSyncScrobbleWorker(
    context: Context,
    workerParameters: WorkerParameters
) : Worker(context, workerParameters) {

    val trackRepository = Injector.get().trackRepo()
    val bookRepository = Injector.get().bookRepo()

    private var workerJob = Job()
    private val workerScope = CoroutineScope(workerJob + Dispatchers.IO)

    override fun doWork(): Result {
        val sourceId = inputData.requireLong(SOURCE_ID_ARG)
        val trackId = inputData.requireInt(TRACK_ID_ARG)
        val playbackState = inputData.requireString(TRACK_STATE_ARG)
        val trackProgress = inputData.requireLong(TRACK_POSITION_ARG)
        val playbackTimeStamp = inputData.requireLong(PLAYBACK_TIME_STAMP_ARG)
        val bookProgress = inputData.requireLong(BOOK_PROGRESS_ARG)

        val source = Injector.get().sourceManager().getSourceById(sourceId)
        check(source is HttpMediaSource)

        // Ensure user has server authorization scrobble data
        if (!source.isAuthorized()) {
            return Result.failure()
        }

        try {
            workerScope.launch(Injector.get().unhandledExceptionHandler()) {
                val track = trackRepository.getTrackAsync(trackId)
                val bookId = track?.parentServerId ?: NO_AUDIOBOOK_FOUND_ID
                val book = bookRepository.getAudiobookAsync(bookId, sourceId)
                val tracks = trackRepository.getTracksForAudiobookAsync(bookId, sourceId)

                check(bookId != NO_AUDIOBOOK_FOUND_ID)
                check(trackId != TRACK_NOT_FOUND && track != null)

                try {
                    source.updateProgress(
                        trackId = trackId.toString(),
                        trackProgress = trackProgress.toString(),
                        playbackTime = playbackTimeStamp,
                        playQueueItemId = track.playQueueItemID,
                        key = "${MediaItemTrack.PARENT_KEY_PREFIX}$trackId",
                        // IMPORTANT: Plex normally marks as finished at 90% progress, but it
                        // calculates progress with respect to duration provided if a duration is
                        // provided, so passing duration = actualDuration * 2 causes Plex to never
                        // automatically mark as finished
                        duration = track.duration * 2,
                        playState = playbackState,
                        hasMde = 1
                    )
                    Timber.i("Synced progress for ${book?.title}")
                } catch (t: Throwable) {
                    Timber.e("Failed to sync progress: ${t.message}")
                }

                // Consider track finished when it is within 1 second of it's end
                val isTrackFinished = trackProgress > track.duration - 1
                if (isTrackFinished) {
                    try {
                        source.watched(trackId)
                        Timber.i("Updated watch status for: ${track.title}")
                    } catch (t: Throwable) {
                        Timber.e("Failed to update track watched status: ${t.message}")
                    }
                }

                // Consider the book finished when playback pauses or stops the book with less than
                // [BOOK_FINISHED_WINDOW] milliseconds remaining
                val isBookAlmostEnded =
                    tracks.getDuration() - bookProgress < BOOK_FINISHED_END_OFFSET_MILLIS
                val hasUserEndedPlayback =
                    playbackState == PLEX_STATE_STOPPED || playbackState == PLEX_STATE_PAUSED
                val isBookFinished = isBookAlmostEnded && hasUserEndedPlayback

                if (isBookFinished) {
                    try {
                        source.watched(bookId)
                        Timber.i("Updated watch status for: ${book?.title}")
                    } catch (t: Throwable) {
                        Timber.e("Failed to update book watched status: ${t.message}")
                    }
                }
            }
        } catch (e: Throwable) {
            Timber.e("Error occurred while syncing watched status! $e")
            return Result.failure()
        }

        return Result.success()
    }

    override fun onStopped() {
        workerJob.cancel()
        super.onStopped()
    }

    companion object {
        const val SOURCE_ID_ARG = "source id"
        const val TRACK_ID_ARG = "Track ID"
        const val TRACK_STATE_ARG = "State"
        const val TRACK_POSITION_ARG = "Track position"
        const val PLAYBACK_TIME_STAMP_ARG = "Original play time"
        const val BOOK_PROGRESS_ARG = "Book progress"

        fun makeWorkerData(
            sourceId: Long,
            trackId: Int,
            playbackState: String,
            trackProgress: Long,
            bookProgress: Long,
            playbackTimeStamp: Long = System.currentTimeMillis()
        ): Data {
            require(trackId != TRACK_NOT_FOUND)
            return workDataOf(
                SOURCE_ID_ARG to sourceId,
                TRACK_ID_ARG to trackId,
                TRACK_POSITION_ARG to trackProgress,
                TRACK_STATE_ARG to playbackState,
                PLAYBACK_TIME_STAMP_ARG to playbackTimeStamp,
                BOOK_PROGRESS_ARG to bookProgress
            )
        }
    }

    private fun Data.requireInt(key: String): Int {
        require(hasKeyWithValueOfType<Int>(key))
        return getInt(key, -1)
    }

    private fun Data.requireLong(key: String): Long {
        require(hasKeyWithValueOfType<Long>(key))
        return getLong(key, -1L)
    }

    private fun Data.requireString(key: String): String {
        require(hasKeyWithValueOfType<String>(key))
        return getString(key) ?: ""
    }
}