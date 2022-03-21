package io.github.mattpvaughn.chronicle.features.player

import android.os.Build
import android.os.Build.VERSION_CODES.M
import android.os.Bundle
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD
import android.view.KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD
import com.google.android.exoplayer2.ControlDispatcher
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector.CustomActionProvider
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo


/**
 * The custom actions provided to [MediaSessionConnector.setCustomActionProviders()] for the app
 */
fun makeCustomActionProviders(
    trackListStateManager: TrackListStateManager,
    mediaSessionConnector: MediaSessionConnector,
    prefsRepo: PrefsRepo
): Array<CustomActionProvider> {
    return arrayOf(
        SimpleCustomActionProvider(SKIP_BACKWARDS) { player: Player, _: String, _: Bundle? ->
            player.seekRelative(trackListStateManager, SKIP_BACKWARDS_DURATION_MS_SIGNED)
        },
        SimpleCustomActionProvider(makeSkipForward(prefsRepo)) { player: Player, _: String, _: Bundle? ->
            player.seekRelative(trackListStateManager, prefsRepo.jumpForwardSeconds)
        },
        SimpleCustomActionProvider(makeChangeSpeed(prefsRepo)) { player: Player, _: String, _: Bundle? ->
            changeSpeed(trackListStateManager, mediaSessionConnector, prefsRepo)
        }
    )

}

fun changeSpeed(
    trackListStateManager: TrackListStateManager,
    mediaSessionConnector: MediaSessionConnector,
    prefsRepo: PrefsRepo
) {
    when (prefsRepo.playbackSpeed) {
        0.5f -> prefsRepo.playbackSpeed = 0.7f
        0.7f -> prefsRepo.playbackSpeed = 1.0f
        1.0f -> prefsRepo.playbackSpeed = 1.2f
        1.2f -> prefsRepo.playbackSpeed = 1.5f
        1.5f -> prefsRepo.playbackSpeed = 1.7f
        1.7f -> prefsRepo.playbackSpeed = 2.0f
        2.0f -> prefsRepo.playbackSpeed = 3.0f
        3.0f -> prefsRepo.playbackSpeed = 0.5f
        else -> prefsRepo.playbackSpeed = 1.0f
    }
    mediaSessionConnector.setCustomActionProviders(
        *makeCustomActionProviders(
            trackListStateManager,
            mediaSessionConnector,
            prefsRepo
        )
    )
}

// const val SKIP_FORWARDS_DURATION_MS_SIGNED = 30000L
const val SKIP_BACKWARDS_DURATION_MS_SIGNED = -10000L

const val SKIP_FORWARDS_STRING = "Skip forwards"
/*
val SKIP_FORWARDS: PlaybackStateCompat.CustomAction = PlaybackStateCompat.CustomAction.Builder(
    SKIP_FORWARDS_STRING,
    SKIP_FORWARDS_STRING,
    R.drawable.ic_forward_30_white
).build()
*/

fun makeSkipForward(
    prefsRepo: PrefsRepo
): PlaybackStateCompat.CustomAction {
    val drawable: Int = when (prefsRepo.jumpForwardSeconds) {
        10L -> R.drawable.ic_forward_5_white
        15L -> R.drawable.ic_replay_10_white
        20L -> R.drawable.ic_forward_30_white
        30L -> R.drawable.ic_forward_30_white
        60L -> R.drawable.ic_forward_30_white
        90L -> R.drawable.ic_forward_30_white
        else -> R.drawable.ic_search_white
    }
    return PlaybackStateCompat.CustomAction.Builder(
        SKIP_FORWARDS_STRING,
        SKIP_FORWARDS_STRING,
        drawable
    ).build()
}



const val SKIP_BACKWARDS_STRING = "Skip backwards"
val SKIP_BACKWARDS: PlaybackStateCompat.CustomAction = PlaybackStateCompat.CustomAction.Builder(
    SKIP_BACKWARDS_STRING,
    SKIP_BACKWARDS_STRING,
    R.drawable.ic_replay_10_white
).build()

const val CHANGE_PLAYBACK_SPEED = "Change Speed"

fun makeChangeSpeed(
    prefsRepo: PrefsRepo
): PlaybackStateCompat.CustomAction {
    val drawable: Int = when (prefsRepo.playbackSpeed) {
        0.5f -> R.drawable.ic_speed_up_0_5x
        0.7f -> R.drawable.ic_speed_up_0_7x
        1.0f -> R.drawable.ic_speed_up_1_0x
        1.2f -> R.drawable.ic_speed_up_1_2x
        1.5f -> R.drawable.ic_speed_up_1_5x
        1.7f -> R.drawable.ic_speed_up_1_7x
        2.0f -> R.drawable.ic_speed_up_2_0x
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
