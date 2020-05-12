package io.github.mattpvaughn.chronicle.data.plex

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.util.Log
import androidx.work.*
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository.Companion.TRACK_NOT_FOUND
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class PlexSyncScrobbleWorker(
    context: Context,
    workerParameters: WorkerParameters
) : Worker(context, workerParameters) {

    val library = SharedPreferencesPlexPrefsRepo(context.getSharedPreferences(APP_NAME, MODE_PRIVATE)).getLibrary()
    val trackRepository = Injector.get().trackRepo()
    val plexConfig = Injector.get().plexConfig()

    private var workerJob = Job()
    private val coroutineScope = CoroutineScope(workerJob + Dispatchers.Main)

    override fun doWork(): Result {
        // Ensure user is logged in before trying to sync scrobble data
        if (Injector.get().plexPrefs().getAuthToken() == "") {
            return Result.failure()
        }
        val trackId = inputData.getIntRequireExists(TRACK_ID_ARG)
        val state = inputData.getStringRequireExists(TRACK_STATE_ARG)
        val position = inputData.getLongRequireExists(TRACK_POSITION_ARG)
        val playbackTimeStamp = inputData.getLongRequireExists(PLAYBACK_TIME_STAMP)
        try {
            coroutineScope.launch {
                val track = trackRepository.getTrackAsync(trackId)
                val bookId = trackRepository.getBookIdForTrack(trackId)
                if (bookId == TRACK_NOT_FOUND) {
                    // give up
                    throw Exception("Track not found! $trackId")
                }
                Log.i(APP_NAME, "Updating remote progress: state = $state, track = ${track?.copy(lastViewedAt = playbackTimeStamp, progress = position)}")
                val call = Injector.get().plexMediaService().progress(
                    ratingKey = trackId.toString(),
                    offset = position.toString(),
                    playbackTime = 0L,
                    playQueueItemId = track?.playQueueItemID ?: 0L,
                    key = "${MediaItemTrack.PARENT_KEY_PREFIX}$trackId",
                    duration = track?.duration ?: 0,
                    playState = state,
                    hasMde = 1
                )
                call.enqueue(object : Callback<Unit> {
                    override fun onFailure(call: Call<Unit>, t: Throwable) {
                        throw t
                    }

                    override fun onResponse(call: Call<Unit>, response: Response<Unit>) {
                        //                    Log.i(APP_NAME, "Sync success: ${response.raw()}")
                    }
                })
            }
        } catch (e: Exception) {
            Log.e(APP_NAME, "Error occurred while syncing watched status! $e")
            return Result.failure()
        }

        return Result.success()
    }

    override fun onStopped() {
        workerJob.cancel()
        super.onStopped()
    }

    companion object {
        val TRACK_ID_ARG = "trackid"
        val TRACK_STATE_ARG = "state"
        val TRACK_POSITION_ARG = "trackposition"
        val PLAYBACK_TIME_STAMP = "orignial play time"

        fun makeWorkerData(trackId: Int, state: String, position: Long, playbackTimeStamp: Long = System.currentTimeMillis()): Data {
            require(trackId != TRACK_NOT_FOUND)
            return workDataOf(
                TRACK_ID_ARG to trackId,
                TRACK_POSITION_ARG to position,
                TRACK_STATE_ARG to state,
                PLAYBACK_TIME_STAMP to playbackTimeStamp
            )
        }
    }

    private fun Data.getIntRequireExists(key: String): Int {
        require (hasKeyWithValueOfType<Int>(key))
        return getInt(key, -1)
    }

    private fun Data.getLongRequireExists(key: String): Long {
        require (hasKeyWithValueOfType<Long>(key))
        return getLong(key, -1L)
    }

    private fun Data.getStringRequireExists(key: String): String {
        require (hasKeyWithValueOfType<String>(key))
        return getString(key) ?: ""
    }
}