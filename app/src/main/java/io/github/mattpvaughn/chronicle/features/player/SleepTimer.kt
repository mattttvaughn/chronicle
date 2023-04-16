package io.github.mattpvaughn.chronicle.features.player

import android.app.Service
import android.hardware.SensorManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.support.v4.media.session.MediaControllerCompat
import android.widget.Toast
import com.squareup.seismic.ShakeDetector
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import io.github.mattpvaughn.chronicle.features.player.SleepTimer.SleepTimerAction.*
import io.github.mattpvaughn.chronicle.util.showToast
import io.github.mattpvaughn.chronicle.views.BottomSheetChooser
import timber.log.Timber
import javax.inject.Inject

/**
 * A countdown timer which pauses playback at the end of countdown.
 *
 * Note: only counts down while playback is active
 */
interface SleepTimer {
    fun cancel()
    fun start(justStarting: Boolean = false)
    fun update(timeRemaining: Long)
    fun extend(extensionDurationMS: Long)
    fun handleAction(action: SleepTimerAction, durationMillis: Long)

    companion object {
        const val ACTION_SLEEP_TIMER_CHANGE = "action sleep timer change"
        const val ARG_SLEEP_TIMER_ACTION = "arg sleep timer action"
        const val ARG_SLEEP_TIMER_DURATION_MILLIS = "sleep_timer_duration"
    }

    enum class SleepTimerAction {
        BEGIN, EXTEND, CANCEL, UPDATE
    }

    interface SleepTimerBroadcaster {
        fun broadcastUpdate(sleepTimerAction: SleepTimerAction, durationMillis: Long = 0L)
    }
}

class SimpleSleepTimer @Inject constructor(
    private val service: Service,
    private val broadcastManager: SleepTimer.SleepTimerBroadcaster,
    private val mediaController: MediaControllerCompat,
    private val sensorManager: SensorManager,
    private val toneGenerator: ToneGenerator,
    private val prefsRepo: PrefsRepo
) : SleepTimer {

    private val sleepTimerUpdateFrequencyMs = 1000L
    private var sleepTimeRemaining = 0L
    private val sleepTimerHandler = Handler(Looper.getMainLooper())
    private val updateSleepTimerAction = { start(false) }
    private var isActive: Boolean = false
    private val shakeToSnoozeDurationMs = 5 * 60 * 1000L
    private val shakeOccurredSoundDurationMs = 150
    private val shakeDetector = ShakeDetector(
        ShakeDetector.Listener {
            Timber.i("Shake detected. Extending")
            if (prefsRepo.shakeToSnooze) {
                extend(shakeToSnoozeDurationMs)
                toneGenerator.startTone(ToneGenerator.TONE_CDMA_PIP, shakeOccurredSoundDurationMs)
                showToast(
                    service,
                    BottomSheetChooser.FormattableString.from(R.string.sleep_timer_extended_message),
                    Toast.LENGTH_SHORT
                )
            }
        }
    )

    // TODO: handle changes to playback speed?
    override fun handleAction(action: SleepTimer.SleepTimerAction, durationMillis: Long) {
        when (action) {
            UPDATE -> update(durationMillis)
            EXTEND -> extend(durationMillis)
            CANCEL -> cancel()
            BEGIN -> {
                update(durationMillis)
                start(true)
            }
        }
    }

    override fun cancel() {
        // no need to broadcast a cancel, the cancel has to come from the UI, and the UI for the
        // sleep timer is a single point as of now
        Timber.i("Sleep timer canceled")
        shakeDetector.stop()
        sleepTimerHandler.removeCallbacksAndMessages(null)
        isActive = false
        sleepTimeRemaining = 0
    }

    override fun start(justStarting: Boolean) {
        // Cannot start a new timer if there is already one active. We return instead of throwing an
        // exception because downside of just ignoring the new sleep timer is small, as normal user
        // behavior should avoid this being called
        if (isActive && justStarting) {
            return
        }
        if (justStarting) {
            shakeDetector.start(sensorManager, SensorManager.SENSOR_DELAY_GAME)
        }
        Timber.i("Sleep timer tick: $sleepTimeRemaining ms remaining")
        if (sleepTimeRemaining > 0L) {
            isActive = true
            if (mediaController.playbackState.isPlaying) {
                sleepTimeRemaining -= sleepTimerUpdateFrequencyMs
            }
            broadcastManager.broadcastUpdate(UPDATE, sleepTimeRemaining)
            sleepTimerHandler.postDelayed(updateSleepTimerAction, sleepTimerUpdateFrequencyMs)
        } else {
            cancel()
            mediaController.transportControls.pause()
        }
    }

    override fun update(timeRemaining: Long) {
        sleepTimeRemaining = timeRemaining
    }

    override fun extend(extensionDurationMS: Long) {
        Timber.i("Sleep timer extended by $extensionDurationMS milliseconds")
        sleepTimeRemaining += extensionDurationMS
    }
}
