package io.github.mattpvaughn.chronicle.features.player

import android.os.Build
import android.os.Build.VERSION_CODES.M
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent.KEYCODE_MEDIA_NEXT
import android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
import android.view.KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD
import android.view.KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo

fun buildCustomActions(prefsRepo: PrefsRepo): List<PlaybackStateCompat.CustomAction> =
    listOf(
        makeSkipBackward(prefsRepo),
        makeSkipForward(prefsRepo),
        SKIP_TO_PREVIOUS,
        SKIP_TO_NEXT,
    )

/** Threshold to decide whether to jump to the beginning of the current chapter or to the previous chapter. */
const val SKIP_TO_PREVIOUS_CHAPTER_THRESHOLD_SECONDS = 30L

const val SKIP_TO_NEXT_STRING = "Skip to next"
val SKIP_TO_NEXT: PlaybackStateCompat.CustomAction =
    PlaybackStateCompat.CustomAction.Builder(
        SKIP_TO_NEXT_STRING,
        SKIP_TO_NEXT_STRING,
        R.drawable.ic_skip_next_white,
    ).build()

const val SKIP_TO_PREVIOUS_STRING = "Skip to previous"
val SKIP_TO_PREVIOUS: PlaybackStateCompat.CustomAction =
    PlaybackStateCompat.CustomAction.Builder(
        SKIP_TO_PREVIOUS_STRING,
        SKIP_TO_PREVIOUS_STRING,
        R.drawable.ic_skip_previous_white,
    ).build()

const val SKIP_FORWARDS_STRING = "Skip forwards"

fun makeSkipForward(prefsRepo: PrefsRepo): PlaybackStateCompat.CustomAction {
    val drawable: Int =
        when (prefsRepo.jumpForwardSeconds) {
            10L -> R.drawable.ic_forward_10_white
            15L -> R.drawable.ic_forward_15_white
            20L -> R.drawable.ic_forward_20_white
            30L -> R.drawable.ic_forward_30_white
            60L -> R.drawable.ic_forward_60_white
            90L -> R.drawable.ic_forward_90_white
            else -> R.drawable.ic_forward_30_white
        }
    return PlaybackStateCompat.CustomAction.Builder(
        SKIP_FORWARDS_STRING,
        SKIP_FORWARDS_STRING,
        drawable,
    ).build()
}

const val SKIP_BACKWARDS_STRING = "Skip backwards"

fun makeSkipBackward(prefsRepo: PrefsRepo): PlaybackStateCompat.CustomAction {
    val drawable: Int =
        when (prefsRepo.jumpBackwardSeconds) {
            10L -> R.drawable.ic_replay_10_white
            15L -> R.drawable.ic_replay_15_white
            20L -> R.drawable.ic_replay_20_white
            30L -> R.drawable.ic_replay_30_white
            60L -> R.drawable.ic_replay_60_white
            90L -> R.drawable.ic_replay_90_white
            else -> R.drawable.ic_replay_10_white
        }
    return PlaybackStateCompat.CustomAction.Builder(
        SKIP_BACKWARDS_STRING,
        SKIP_BACKWARDS_STRING,
        drawable,
    ).build()
}

val mediaSkipForwardCode = if (Build.VERSION.SDK_INT >= M) KEYCODE_MEDIA_SKIP_FORWARD else 272
val mediaSkipBackwardCode = if (Build.VERSION.SDK_INT >= M) KEYCODE_MEDIA_SKIP_BACKWARD else 273
val mediaSkipToNextCode = if (Build.VERSION.SDK_INT >= M) KEYCODE_MEDIA_NEXT else 87
val mediaSkipToPreviousCode = if (Build.VERSION.SDK_INT >= M) KEYCODE_MEDIA_PREVIOUS else 88
