package io.github.mattpvaughn.chronicle.features.player

import android.os.Bundle
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector.CustomActionProvider
import io.github.mattpvaughn.chronicle.features.player.AudiobookMediaSessionCallback.TrackListStateManager

fun makeCustomActionProviders(
    sleepTimer: SleepTimer,
    trackListStateManager: TrackListStateManager
): Array<CustomActionProvider> {
    return arrayOf(
        SimpleCustomActionProvider(SKIP_FORWARDS) { player: Player, _: String, _: Bundle? ->
            skipForwards(player, trackListStateManager)
        },
        SimpleCustomActionProvider(SKIP_BACKWARDS) { player: Player, _: String, _: Bundle? ->
            skipBackwards(player, trackListStateManager)
        },
        SimpleCustomActionProvider(START_SLEEP_TIMER) { _: Player, _: String, extras: Bundle? ->
            startSleepTimer(extras, sleepTimer)
        },
        SimpleCustomActionProvider(EXTEND_SLEEP_TIMER) { _: Player, _: String, extras: Bundle? ->
            extendSleepTimer(extras, sleepTimer)
        },
        SimpleCustomActionProvider(CANCEL_SLEEP_TIMER) { _: Player, _: String, _: Bundle? ->
            sleepTimer.cancel()
        }
    )

}

private fun extendSleepTimer(
    extras: Bundle?,
    sleepTimer: SleepTimer
) {
    if (extras == null) {
        throw IllegalArgumentException("Sleep timer requires a duration pass to custom action corresponding to KEY_SLEEP_TIMER_DURATION!")
    }
    val extensionDurationMillis = extras.getLong(
        KEY_SLEEP_TIMER_DURATION_MILLIS,
        AudiobookMediaSessionCallback.NO_LONG_FOUND
    )
    check(extensionDurationMillis != AudiobookMediaSessionCallback.NO_LONG_FOUND)
    sleepTimer.extend(extensionDurationMillis)
}

private fun startSleepTimer(
    extras: Bundle?,
    sleepTimer: SleepTimer
) {
    if (extras == null) {
        throw IllegalArgumentException("Sleep timer requires a duration pass to custom action corresponding to KEY_SLEEP_TIMER_DURATION!")
    }
    val durationMillis = extras.getLong(
        KEY_SLEEP_TIMER_DURATION_MILLIS,
        AudiobookMediaSessionCallback.NO_LONG_FOUND
    )
    check(durationMillis != AudiobookMediaSessionCallback.NO_LONG_FOUND)
    sleepTimer.update(durationMillis)
    sleepTimer.start()
}

private fun skipBackwards(
    player: Player,
    trackListStateManager: TrackListStateManager
) {
    if (!player.isLoading) {
        // Update [trackListStateManager] to reflect the current playback state
        trackListStateManager.currentTrackIndex = player.currentWindowIndex
        trackListStateManager.currentPosition = player.currentPosition
    }
    trackListStateManager.seekByRelative(SKIP_BACKWARDS_DURATION_MS)
    player.seekTo(
        trackListStateManager.currentTrackIndex,
        trackListStateManager.currentPosition
    )
}

private fun skipForwards(
    player: Player,
    trackListStateManager: TrackListStateManager
) {
    if (!player.isLoading) {
        // Update [trackListStateManager] to reflect the current playback state
        trackListStateManager.currentTrackIndex = player.currentWindowIndex
        trackListStateManager.currentPosition = player.currentPosition
    }
    trackListStateManager.seekByRelative(SKIP_FORWARDS_DURATION_MS)
    player.seekTo(
        trackListStateManager.currentTrackIndex,
        trackListStateManager.currentPosition
    )
}