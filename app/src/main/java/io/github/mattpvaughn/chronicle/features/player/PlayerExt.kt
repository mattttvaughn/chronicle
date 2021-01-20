package io.github.mattpvaughn.chronicle.features.player

import com.google.android.exoplayer2.Player
import timber.log.Timber
import kotlin.math.abs


/**
 * Seek in the play queue by an offset of [durationMillis]. Positive [duration] seeks forwards,
 * negative [duration] seeks backwards
 */
fun Player.seekRelative(trackListStateManager: TrackListStateManager, durationMillis: Long) {
    // if seeking within the current track, no need to calculate seek
    if (durationMillis > 0 && (duration - currentPosition) > durationMillis) {
        Timber.i("Seeking forwards within window: pos = $currentPosition, window duration = $duration, seek= $durationMillis")
        seekTo(currentPosition + durationMillis)
    } else if (durationMillis < 0 && currentPosition > abs(durationMillis)) {
        Timber.i("Seeking backwards within window: pos = $currentPosition, duration = $durationMillis")
        seekTo(currentPosition + durationMillis)
    } else {
        Timber.i("Seeking via trackliststatemanager")
        trackListStateManager.updatePosition(currentWindowIndex, currentPosition)
        trackListStateManager.seekByRelative(durationMillis)
        seekTo(trackListStateManager.currentTrackIndex, trackListStateManager.currentTrackProgress)
    }
}
