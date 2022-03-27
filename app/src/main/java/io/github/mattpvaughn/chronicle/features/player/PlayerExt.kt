package io.github.mattpvaughn.chronicle.features.player

import android.view.Gravity
import android.widget.Toast
import com.google.android.exoplayer2.Player
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.application.MILLIS_PER_SECOND
import io.github.mattpvaughn.chronicle.features.currentlyplaying.CurrentlyPlaying
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

/** Skip to next chapter */
fun Player.skipToNext(
    trackListStateManager: TrackListStateManager,
    currentlyPlaying: CurrentlyPlaying,
    progressUpdater: ProgressUpdater
) {
    Timber.i("Player.skipToNext called")
    val currentChapterIndex = currentlyPlaying.book.value.chapters.indexOf(currentlyPlaying.chapter.value)
    val nextChapterIndex = currentChapterIndex + 1
    if (nextChapterIndex < currentlyPlaying.book.value.chapters.size) {
        val nextChapter = currentlyPlaying.book.value.chapters[nextChapterIndex]
        Timber.d("NEXT CHAPTER: index=$nextChapterIndex id=${nextChapter.id} trackId=${nextChapter.trackId}  offset=${nextChapter.startTimeOffset} title=${nextChapter.title}")
        val containingTrack = trackListStateManager.trackList
            .firstOrNull {
                it.id.toLong() == nextChapter.trackId
            }
        val containingTrackIndex = trackListStateManager.trackList.indexOf(containingTrack)
        seekTo(containingTrackIndex, nextChapter.startTimeOffset + 300)
        progressUpdater.updateProgressWithoutParameters()
    } else {
        val toast = Toast.makeText(
            Injector.get().applicationContext(),
            R.string.skip_forwards_reached_last_chapter,
            Toast.LENGTH_LONG
        )
        toast.setGravity(Gravity.BOTTOM, 0, 200)
        toast.show()
    }
}

/** Skip to previous chapter */
fun Player.skipToPrevious(
    trackListStateManager: TrackListStateManager,
    currentlyPlaying: CurrentlyPlaying,
    progressUpdater: ProgressUpdater
) {
    Timber.i("Player.skipToPrevious called")
    val currentChapterIndex = currentlyPlaying.book.value.chapters.indexOf(currentlyPlaying.chapter.value)
    var previousChapterIndex: Int =
        if ((currentPosition - currentlyPlaying.chapter.value.startTimeOffset) < (SKIP_TO_PREVIOUS_CHAPTER_THRESHOLD_SECONDS * MILLIS_PER_SECOND)) {
            Timber.d("skipToPrevious → skip to previous chapter")
            currentChapterIndex - 1
        } else {
            Timber.d("skipToPrevious → back to start of current chapter")
            currentChapterIndex
        }
    if (previousChapterIndex < 0) previousChapterIndex = 0
    val previousChapter = currentlyPlaying.book.value.chapters[previousChapterIndex]
    Timber.d("PREVIOUS CHAPTER: index=$previousChapterIndex id=${previousChapter.id} trackId=${previousChapter.trackId}  offset=${previousChapter.startTimeOffset} title=${previousChapter.title}")
    val containingTrack = trackListStateManager.trackList
        .firstOrNull {
            it.id.toLong() == previousChapter.trackId
        }
    val containingTrackIndex = trackListStateManager.trackList.indexOf(containingTrack)
    seekTo(containingTrackIndex, previousChapter.startTimeOffset)
    progressUpdater.updateProgressWithoutParameters()
}