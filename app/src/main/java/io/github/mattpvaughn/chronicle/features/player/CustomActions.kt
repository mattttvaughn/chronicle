package io.github.mattpvaughn.chronicle.features.player

import android.os.Build
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import io.github.mattpvaughn.chronicle.R


const val SKIP_FORWARDS_DURATION_MS = 30000L
const val SKIP_BACKWARDS_DURATION_MS = -10000L

const val SKIP_FORWARDS_STRING = "skip_forwards"
val SKIP_FORWARDS: PlaybackStateCompat.CustomAction = PlaybackStateCompat.CustomAction.Builder(
    SKIP_FORWARDS_STRING,
    "Skip forwards",
    R.drawable.ic_forward_30_white
).build()

const val SKIP_BACKWARDS_STRING = "skip_backwards"
val SKIP_BACKWARDS: PlaybackStateCompat.CustomAction = PlaybackStateCompat.CustomAction.Builder(
    SKIP_BACKWARDS_STRING,
    "Skip backwards",
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


const val START_SLEEP_TIMER_STRING = "start_sleep_timer"
const val UPDATE_SLEEP_TIMER_STRING = "update_sleep_timer"
const val KEY_SLEEP_TIMER_DURATION_MILLIS = "sleep_timer_duration"
const val KEY_SLEEP_TIMER_ACTION = "sleep_timer_action"
const val SLEEP_TIMER_ACTION_UPDATE_TIME = "update"
const val SLEEP_TIMER_ACTION_EXTEND = "extend"
const val SLEEP_TIMER_ACTION_CANCEL = "cancel"
val START_SLEEP_TIMER: PlaybackStateCompat.CustomAction = PlaybackStateCompat.CustomAction.Builder(
    START_SLEEP_TIMER_STRING,
    "Start sleep timer",
    R.drawable.ic_sleep_timer
).build()
