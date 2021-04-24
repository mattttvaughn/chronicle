package io.github.mattpvaughn.chronicle.features.player

import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import io.github.mattpvaughn.chronicle.data.model.getActiveTrack
import io.github.mattpvaughn.chronicle.data.model.getTrackStartTime
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.max

/**
 * Shadows the state of tracks in the queue in order to calculate seeks for
 * [AudiobookMediaSessionCallback] with information that exoplayer's window management doesn't
 * have (access to track durations outside of current track)
 */
class TrackListStateManager {
    /** The list of [MediaItemTrack]s currently playing */
    var trackList: List<MediaItemTrack> = emptyList()

    /** The index of the current track within [trackList] */
    var currentTrackIndex: Int = 0
        private set

    /** The number of milliseconds from the start of the currently playing track */
    var currentTrackProgress: Long = 0
        private set

    private val currentTrack: MediaItemTrack
        get() = trackList[currentTrackIndex]

    /**
     * The number of milliseconds between current playback position and the start of the first
     * track. This is not authoritative, as [MediaItemTrack.duration] is not necessarily correct
     */
    val currentBookPosition: Long
        get() = trackList.getTrackStartTime(currentTrack) + currentTrackProgress

    /**
     * Update [currentTrackIndex] to [activeTrackIndex] and [currentTrackProgress] to
     * [offsetFromTrackStart]
     */
    fun updatePosition(activeTrackIndex: Int, offsetFromTrackStart: Long) {
        if (activeTrackIndex >= trackList.size) {
            throw IndexOutOfBoundsException("Cannot set current track index = $activeTrackIndex if tracklist.size == ${trackList.size}")
        }
        currentTrackIndex = activeTrackIndex
        currentTrackProgress = offsetFromTrackStart
    }

    /**
     * Update position based on tracks in [trackList], picking the one with the most recent
     * [MediaItemTrack.lastViewedAt]
     */
    fun seekToActiveTrack() {
        Timber.i("Seeking to active track")
        val activeTrack = trackList.getActiveTrack()
        currentTrackIndex = trackList.indexOf(activeTrack)
        currentTrackProgress = activeTrack.progress
    }

    /** Seeks forwards or backwards in the playlist by [offsetMillis] millis*/
    fun seekByRelative(offsetMillis: Long) {
        if (offsetMillis >= 0) {
            seekForwards(offsetMillis)
        } else {
            seekBackwards(abs(offsetMillis))
        }
    }

    /** Seek backwards by [offset] ms. [offset] must be a positive [Long] */
    private fun seekBackwards(offset: Long) {
        check(offset >= 0) { "Attempted to seek by a negative number: $offset" }
        var offsetRemaining =
            offset + (trackList[currentTrackIndex].duration - currentTrackProgress)
        for (index in currentTrackIndex downTo 0) {
            if (offsetRemaining < trackList[index].duration) {
                currentTrackProgress = max(0, trackList[index].duration - offsetRemaining)
                currentTrackIndex = index
                return
            } else {
                offsetRemaining -= trackList[index].duration
            }
        }
        currentTrackIndex = 0
        currentTrackProgress = 0
    }

    private fun seekForwards(offset: Long) {
        check(offset >= 0) { "Attempted to seek by a negative number: $offset" }
        var offsetRemaining = offset + currentTrackProgress
        for (index in currentTrackIndex until trackList.size) {
            if (offsetRemaining < trackList[index].duration) {
                currentTrackIndex = index
                currentTrackProgress = offsetRemaining
                return
            } else {
                offsetRemaining -= trackList[index].duration
            }
        }
        currentTrackIndex = trackList.size - 1
        currentTrackProgress = trackList.lastOrNull()?.duration ?: 0L
    }
}