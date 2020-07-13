package io.github.mattpvaughn.chronicle.features.player

import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import io.github.mattpvaughn.chronicle.data.model.getActiveTrack
import io.github.mattpvaughn.chronicle.data.model.getTrackProgressInAudiobook
import io.github.mattpvaughn.chronicle.data.model.getTrackStartTime
import io.github.mattpvaughn.chronicle.data.sources.plex.model.getDuration
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Shadows the state of tracks in the queue in order to calculate seeks for
 * [AudiobookMediaSessionCallback] with information that exoplayer's window management doesn't
 * have (access to track durations outside of current track)
 */
class TrackListStateManager {
    /** The list of [MediaItemTrack]s currently playing */
    var trackList: List<MediaItemTrack> = emptyList()

    /**
     * The index of the current track within [trackList]. This is just a convenience getter
     * for the single source of truth, the currentPosition
     */
    val currentTrackIndex: Int
        get() {
            var timeConsumed = 0L
            for ((index, track) in trackList.withIndex()) {
                if (currentBookPosition - timeConsumed < track.duration) {
                    return index
                }
                timeConsumed += track.duration
            }
            return trackList.size - 1
        }

    /** The number of milliseconds from the start of the currently playing track */
    val currentTrackProgress: Long
        get() {
            var progressToConsume = currentBookPosition
            val trackOffset = trackList.getTrackStartTime(currentTrack)
            progressToConsume -= trackOffset
            return progressToConsume
        }

    private val currentTrack: MediaItemTrack
        get() {
            return trackList[currentTrackIndex]
        }

    /**
     * The number of milliseconds between current playback position and the start of the first
     * track
     */
    var currentBookPosition: Long = 0

    /**
     * Reset [currentBookPosition] to reflect the provided [activeTrackIndex] and
     * [offsetFromTrackStart]. Needed when the track progress in the [trackList] is not
     * available
     */
    fun updatePosition(activeTrackIndex: Int, offsetFromTrackStart: Long) {
        val activeTrack = trackList[activeTrackIndex]
        val previousTrackDurations = trackList.getTrackStartTime(activeTrack)
        currentBookPosition = offsetFromTrackStart + previousTrackDurations
    }

    /*
     * If we want to seek to the most recently active track, accumulate duration of all
     * previously played tracks + progress of current track
     */
    fun seekToActiveTrack() {
        Timber.i("Seeking to active track")
        val activeTrack = trackList.getActiveTrack()
        val previousTrackDurations = trackList.filter { it.index < activeTrack.index }
            .fold(0L) { acc, track -> acc + track.duration }
        currentBookPosition = activeTrack.progress + previousTrackDurations
    }

    /** Seeks forwards or backwards in the playlist by [offset] millis*/
    fun seekByRelative(offset: Long) {
        if (offset >= 0) {
            seekForwards(offset)
        } else {
            seekBackwards(abs(offset))
        }
    }

    /** Seek backwards by [offset] ms. [offset] must be a positive [Long] */
    private fun seekBackwards(offset: Long) {
        check(offset >= 0) { "Attempted to seek by a negative number: $offset" }
        // No negative offsets, minimum is 0
        currentBookPosition = max(currentBookPosition - offset, 0)
    }

    private fun seekForwards(offset: Long) {
        check(offset >= 0L) { "Tried to seekForwards by a positive number" }
        // Don't seek past [trackList.getDuration]
        currentBookPosition = min(
            currentBookPosition + offset,
            trackList.getDuration()
        )
    }

    /**
     * Seeks to track with [MediaItemTrack.id] == [id]
     */
    fun seekToTrack(id: Long) {
        val trackWithId = trackList.find { it.id.toLong() == id }
        if (trackWithId != null) {
            currentBookPosition = trackList.getTrackProgressInAudiobook(trackWithId)
        } else {
            // Track with such id does not exist!
            Timber.e("Attempted to seek to track with id == $id but it does not exist")
        }
    }
}