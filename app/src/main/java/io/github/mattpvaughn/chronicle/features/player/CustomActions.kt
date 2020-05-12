package io.github.mattpvaughn.chronicle.features.player

import android.os.Build
import android.os.Bundle
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import com.google.android.exoplayer2.ControlDispatcher
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import io.github.mattpvaughn.chronicle.R


const val SKIP_FORWARDS_DURATION_MS = 30000L
const val SKIP_BACKWARDS_DURATION_MS = -10000L

const val SKIP_FORWARDS_STRING = "Skip forwards"
val SKIP_FORWARDS: PlaybackStateCompat.CustomAction = PlaybackStateCompat.CustomAction.Builder(
    SKIP_FORWARDS_STRING,
    SKIP_FORWARDS_STRING,
    R.drawable.ic_forward_30_white
).build()

const val SKIP_BACKWARDS_STRING = "Skip backwards"
val SKIP_BACKWARDS: PlaybackStateCompat.CustomAction = PlaybackStateCompat.CustomAction.Builder(
    SKIP_BACKWARDS_STRING,
    SKIP_BACKWARDS_STRING,
    R.drawable.ic_replay_10_white
).build()

val mediaSkipForwardCode: Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD
    } else {
        272
    }

val mediaSkipBackwardCode: Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD
    } else {
        273
    }


const val START_SLEEP_TIMER_STRING = "Start sleep timer"
const val UPDATE_SLEEP_TIMER_STRING = "Update sleep timer"
const val KEY_SLEEP_TIMER_DURATION_MILLIS = "sleep_timer_duration"
const val KEY_SLEEP_TIMER_ACTION = "sleep_timer_action"
const val SLEEP_TIMER_ACTION_UPDATE_TIME = "update"
const val SLEEP_TIMER_ACTION_EXTEND = "Extend sleep timer"
const val SLEEP_TIMER_ACTION_CANCEL = "Cancel sleep timer"
val START_SLEEP_TIMER: PlaybackStateCompat.CustomAction = PlaybackStateCompat.CustomAction.Builder(
    START_SLEEP_TIMER_STRING,
    START_SLEEP_TIMER_STRING,
    R.drawable.ic_sleep_timer
).build()
val EXTEND_SLEEP_TIMER: PlaybackStateCompat.CustomAction = PlaybackStateCompat.CustomAction.Builder(
    SLEEP_TIMER_ACTION_EXTEND,
    SLEEP_TIMER_ACTION_EXTEND,
    R.drawable.ic_sleep_timer
).build()
val CANCEL_SLEEP_TIMER: PlaybackStateCompat.CustomAction = PlaybackStateCompat.CustomAction.Builder(
    SLEEP_TIMER_ACTION_CANCEL,
    SLEEP_TIMER_ACTION_CANCEL,
    R.drawable.ic_sleep_timer
).build()

class SimpleCustomActionProvider(
    private val customAction: PlaybackStateCompat.CustomAction,
    private val action: (player: Player, action: String, extras: Bundle?) -> Unit
) : MediaSessionConnector.CustomActionProvider {
    /** A simple custom action returns itself no matter what the playback state */
    override fun getCustomAction(player: Player): PlaybackStateCompat.CustomAction {
        return customAction
    }

    override fun onCustomAction(
        player: Player,
        controlDispatcher: ControlDispatcher,
        action: String,
        extras: Bundle?
    ) {
        action(player, action, extras)
    }

}
