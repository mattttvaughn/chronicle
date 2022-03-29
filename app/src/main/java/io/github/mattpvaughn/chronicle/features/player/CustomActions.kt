package io.github.mattpvaughn.chronicle.features.player

import android.os.Build
import android.os.Build.VERSION_CODES.M
import android.os.Bundle
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD
import android.view.KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD
import android.view.KeyEvent.KEYCODE_MEDIA_NEXT
import android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
import com.google.android.exoplayer2.ControlDispatcher
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector.CustomActionProvider
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.application.MILLIS_PER_SECOND
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import io.github.mattpvaughn.chronicle.features.currentlyplaying.CurrentlyPlaying


/**
 * The custom actions provided to [MediaSessionConnector.setCustomActionProviders()] for the app
 */
fun makeCustomActionProviders(
    trackListStateManager: TrackListStateManager,
    mediaSessionConnector: MediaSessionConnector,
    prefsRepo: PrefsRepo,
    currentlyPlaying: CurrentlyPlaying,
    progressUpdater: ProgressUpdater
): Array<CustomActionProvider> {
    return arrayOf(
        SimpleCustomActionProvider(makeSkipBackward(prefsRepo)) { player: Player, _: String, _: Bundle? ->
            player.seekRelative(trackListStateManager, prefsRepo.jumpBackwardSeconds * MILLIS_PER_SECOND * -1)
        },
        SimpleCustomActionProvider(makeSkipForward(prefsRepo)) { player: Player, _: String, _: Bundle? ->
            player.seekRelative(trackListStateManager, prefsRepo.jumpForwardSeconds * MILLIS_PER_SECOND)
        },
        SimpleCustomActionProvider(SKIP_TO_NEXT) { player: Player, _: String, _: Bundle? ->
            player.skipToNext(trackListStateManager, currentlyPlaying, progressUpdater)
        },
        SimpleCustomActionProvider(SKIP_TO_PREVIOUS) { player: Player, _: String, _: Bundle? ->
            player.skipToPrevious(trackListStateManager, currentlyPlaying, progressUpdater)
      },
        SimpleCustomActionProvider(makeChangeSpeed(prefsRepo)) { player: Player, _: String, _: Bundle? ->
            changeSpeed(trackListStateManager, mediaSessionConnector, prefsRepo, currentlyPlaying, progressUpdater)
        }
    )
}

fun changeSpeed(
    trackListStateManager: TrackListStateManager,
    mediaSessionConnector: MediaSessionConnector,
    prefsRepo: PrefsRepo,
    currentlyPlaying: CurrentlyPlaying,
    progressUpdater: ProgressUpdater
) {
    when (prefsRepo.playbackSpeed) {
        0.5f -> prefsRepo.playbackSpeed = 0.6f
        0.6f -> prefsRepo.playbackSpeed = 0.7f
        0.7f -> prefsRepo.playbackSpeed = 0.8f
        0.8f -> prefsRepo.playbackSpeed = 0.9f
        0.9f -> prefsRepo.playbackSpeed = 1.0f
        1.0f -> prefsRepo.playbackSpeed = 1.1f
        1.1f -> prefsRepo.playbackSpeed = 1.2f
        1.2f -> prefsRepo.playbackSpeed = 1.3f
        1.3f -> prefsRepo.playbackSpeed = 1.4f
        1.4f -> prefsRepo.playbackSpeed = 1.5f
        1.5f -> prefsRepo.playbackSpeed = 1.6f
        1.6f -> prefsRepo.playbackSpeed = 1.7f
        1.7f -> prefsRepo.playbackSpeed = 1.8f
        1.8f -> prefsRepo.playbackSpeed = 1.9f
        1.9f -> prefsRepo.playbackSpeed = 2.0f
        2.0f -> prefsRepo.playbackSpeed = 2.5f
        2.5f -> prefsRepo.playbackSpeed = 3.0f
        else -> prefsRepo.playbackSpeed = 1.0f
    }
    mediaSessionConnector.setCustomActionProviders(
        *makeCustomActionProviders(
            trackListStateManager,
            mediaSessionConnector,
            prefsRepo,
            currentlyPlaying,
            progressUpdater
        )
    )
}

/** Threshold to decide whether to jump to the beginning of the current chapter or to the previous chapter. */
const val SKIP_TO_PREVIOUS_CHAPTER_THRESHOLD_SECONDS = 30L

const val SKIP_TO_NEXT_STRING = "Skip to next"
val SKIP_TO_NEXT: PlaybackStateCompat.CustomAction = PlaybackStateCompat.CustomAction.Builder(
    SKIP_TO_NEXT_STRING,
    SKIP_TO_NEXT_STRING,
    R.drawable.ic_skip_next_white
).build()

const val SKIP_TO_PREVIOUS_STRING = "Skip to previous"
val SKIP_TO_PREVIOUS: PlaybackStateCompat.CustomAction = PlaybackStateCompat.CustomAction.Builder(
    SKIP_TO_PREVIOUS_STRING,
    SKIP_TO_PREVIOUS_STRING,
    R.drawable.ic_skip_previous_white
).build()

const val SKIP_FORWARDS_STRING = "Skip forwards"

fun makeSkipForward(
    prefsRepo: PrefsRepo
): PlaybackStateCompat.CustomAction {
    val drawable: Int = when (prefsRepo.jumpForwardSeconds) {
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
        drawable
    ).build()
}

const val SKIP_BACKWARDS_STRING = "Skip backwards"

fun makeSkipBackward(
    prefsRepo: PrefsRepo
): PlaybackStateCompat.CustomAction {
    val drawable: Int = when (prefsRepo.jumpBackwardSeconds) {
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
        drawable
    ).build()
}

const val CHANGE_PLAYBACK_SPEED = "Change Speed"

fun makeChangeSpeed(
    prefsRepo: PrefsRepo
): PlaybackStateCompat.CustomAction {
    val drawable: Int = when (prefsRepo.playbackSpeed) {
        0.5f -> R.drawable.ic_speed_up_0_5x
        0.6f -> R.drawable.ic_speed_up_0_6x
        0.7f -> R.drawable.ic_speed_up_0_7x
        0.8f -> R.drawable.ic_speed_up_0_8x
        0.9f -> R.drawable.ic_speed_up_0_9x
        1.0f -> R.drawable.ic_speed_up_1_0x
        1.1f -> R.drawable.ic_speed_up_1_1x
        1.2f -> R.drawable.ic_speed_up_1_2x
        1.3f -> R.drawable.ic_speed_up_1_3x
        1.4f -> R.drawable.ic_speed_up_1_4x
        1.5f -> R.drawable.ic_speed_up_1_5x
        1.6f -> R.drawable.ic_speed_up_1_6x
        1.7f -> R.drawable.ic_speed_up_1_7x
        1.8f -> R.drawable.ic_speed_up_1_8x
        1.9f -> R.drawable.ic_speed_up_1_9x
        2.0f -> R.drawable.ic_speed_up_2_0x
        2.5f -> R.drawable.ic_speed_up_2_5x
        3.0f -> R.drawable.ic_speed_up_3_0x
        else -> R.drawable.ic_speed_up_1_0x
    }
    return PlaybackStateCompat.CustomAction.Builder(
        CHANGE_PLAYBACK_SPEED,
        CHANGE_PLAYBACK_SPEED,
        drawable
    ).build()
}

val mediaSkipForwardCode = if (Build.VERSION.SDK_INT >= M) KEYCODE_MEDIA_SKIP_FORWARD else 272
val mediaSkipBackwardCode = if (Build.VERSION.SDK_INT >= M) KEYCODE_MEDIA_SKIP_BACKWARD else 273
val mediaSkipToNextCode = if (Build.VERSION.SDK_INT >= M) KEYCODE_MEDIA_NEXT else 87
val mediaSkipToPreviousCode = if (Build.VERSION.SDK_INT >= M) KEYCODE_MEDIA_PREVIOUS else 88

class SimpleCustomActionProvider(
    private val customAction: PlaybackStateCompat.CustomAction,
    private val action: (player: Player, action: String, extras: Bundle?) -> Unit
) : CustomActionProvider {
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
