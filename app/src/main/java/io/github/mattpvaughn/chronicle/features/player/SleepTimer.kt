package io.github.mattpvaughn.chronicle.features.player

import android.content.Intent
import android.os.Handler
import android.support.v4.media.session.MediaControllerCompat
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME
import javax.inject.Inject

/**
 * A countdown timer which pauses playback at the end of countdown. Important note: the timer
 * only counts down while playback is active
 */
interface SleepTimer {
    fun cancel()
    fun start()
    fun update(timeRemaining: Long)
    fun extend(extensionDuration: Long)
}

class SimpleSleepTimer @Inject constructor(
    private val broadcastManager: LocalBroadcastManager,
    private val mediaController: MediaControllerCompat
) : SleepTimer {

    private val sleepTimerUpdateFrequencyMs = 1000L
    private var sleepTimeRemaining = 0L
    private val sleepTimerHandler = Handler()
    private val sleepTimerIntent = Intent(UPDATE_SLEEP_TIMER_STRING)
    private val updateSleepTimerAction = { start() }

    override fun cancel() {
        Log.i(APP_NAME, "Sleep timer canceled")
        sleepTimerHandler.removeCallbacks(updateSleepTimerAction)
        sleepTimeRemaining = 0L
        sleepTimerIntent.putExtra(UPDATE_SLEEP_TIMER_STRING, 0L)
        broadcastManager.sendBroadcast(sleepTimerIntent)
    }

    override fun start() {
        if (sleepTimeRemaining > 0L) {
            if (mediaController.playbackState.isPlaying) {
                sleepTimeRemaining -= sleepTimerUpdateFrequencyMs
            }
            Log.i(APP_NAME, "Sleep timer active: $sleepTimeRemaining ms remaining")
            sleepTimerIntent.putExtra(UPDATE_SLEEP_TIMER_STRING, sleepTimeRemaining)
            broadcastManager.sendBroadcast(sleepTimerIntent)
            sleepTimerHandler.postDelayed(updateSleepTimerAction, sleepTimerUpdateFrequencyMs)
        } else {
            mediaController.transportControls.pause()
        }
    }

    override fun update(timeRemaining: Long) {
        sleepTimeRemaining = timeRemaining
    }

    override fun extend(extensionDuration: Long) {
        Log.i(APP_NAME, "Sleep timer extended by $extensionDuration milliseconds")
        sleepTimeRemaining += extensionDuration
    }

}
