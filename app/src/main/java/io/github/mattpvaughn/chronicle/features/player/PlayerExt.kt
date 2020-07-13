package io.github.mattpvaughn.chronicle.features.player

import com.google.android.exoplayer2.Player


/**
 * Seek in the play queue by an offset of [durationMillis]. Positive [duration] seeks forwards,
 * negative [duration] seeks backwards
 */
fun Player.seekRelative(trackListStateManager: TrackListStateManager, durationMillis: Long) {
    if (!isLoading) {
        trackListStateManager.updatePosition(currentWindowIndex, currentPosition)
    }
    trackListStateManager.seekByRelative(durationMillis)
    seekTo(trackListStateManager.currentTrackIndex, trackListStateManager.currentTrackProgress)
}
