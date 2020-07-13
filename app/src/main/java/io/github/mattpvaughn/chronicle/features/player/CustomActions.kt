package io.github.mattpvaughn.chronicle.features.player

import android.os.Build
import android.os.Build.VERSION_CODES.M
import android.os.Bundle
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD
import android.view.KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD
import com.google.android.exoplayer2.ControlDispatcher
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector.CustomActionProvider
import io.github.mattpvaughn.chronicle.R


/**
 * The custom actions provided to [MediaSessionConnector.setCustomActionProviders()] for the app
 */
fun makeCustomActionProviders(
    trackListStateManager: TrackListStateManager
): Array<CustomActionProvider> {
    return arrayOf(
        SimpleCustomActionProvider(SKIP_BACKWARDS) { player: Player, _: String, _: Bundle? ->
            player.seekRelative(trackListStateManager, SKIP_BACKWARDS_DURATION_MS_SIGNED)
        },
        SimpleCustomActionProvider(SKIP_FORWARDS) { player: Player, _: String, _: Bundle? ->
            player.seekRelative(trackListStateManager, SKIP_FORWARDS_DURATION_MS)
        }
    )

}

const val SKIP_FORWARDS_DURATION_MS = 30000L
const val SKIP_BACKWARDS_DURATION_MS_SIGNED = -10000L

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
