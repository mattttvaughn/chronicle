package io.github.mattpvaughn.chronicle.features.currentlyplaying

import android.support.v4.media.session.PlaybackStateCompat
import io.github.mattpvaughn.chronicle.BuildConfig
import io.github.mattpvaughn.chronicle.data.model.*
import io.github.mattpvaughn.chronicle.features.player.EMPTY_PLAYBACK_STATE
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import javax.inject.Singleton

/**
 * A global store of state containing information on the [Audiobook]/[MediaItemTrack]/[Chapter]
 * currently playing and the relevant playback information.
 */
@ExperimentalCoroutinesApi
interface CurrentlyPlaying {
    val book: StateFlow<Audiobook>
    val track: StateFlow<MediaItemTrack>
    val chapter: StateFlow<Chapter>
    val playbackState: PlaybackStateCompat

    fun setOnChapterChangeListener(listener: OnChapterChangeListener)
    fun updateTrack(track: MediaItemTrack)
    fun updateBook(book: Audiobook, tracks: List<MediaItemTrack>)
    fun updateProgress(bookProgress: Long, trackProgress: Long)
}

interface OnChapterChangeListener {
    fun onChapterChange(chapter: Chapter)
}

/**
 * Implementation of [CurrentlyPlaying]. Values default to placeholder values until data is
 * made available (the user
 */
@ExperimentalCoroutinesApi
@Singleton
class CurrentlyPlayingSingleton : CurrentlyPlaying {
    override var book = MutableStateFlow(EMPTY_AUDIOBOOK)
    override var track = MutableStateFlow(EMPTY_TRACK)
    override var chapter = MutableStateFlow(EMPTY_CHAPTER)
    override var playbackState: PlaybackStateCompat = EMPTY_PLAYBACK_STATE

    private var tracks: MutableList<MediaItemTrack> = mutableListOf()
    private var chapters: MutableList<Chapter> = mutableListOf()

    override fun updateTrack(track: MediaItemTrack) {
        this.track.value = track
        tracks.replaceTrack(track)
    }

    private var listener: OnChapterChangeListener? = null

    override fun setOnChapterChangeListener(listener: OnChapterChangeListener) {
        this.listener = listener
    }

    override fun updateBook(book: Audiobook, tracks: List<MediaItemTrack>) {
        Timber.i("Updating book ${book.title.take(30)} in ${this.javaClass.simpleName}")
        this.book.value = book

        this.tracks.clear()
        this.tracks.addAll(tracks)

        this.chapters.clear()
        this.chapters.addAll(
            if (book.chapters.isNotEmpty()) {
                book.chapters
            } else {
                tracks.asChapterList()
            }
        )
    }

    override fun updateProgress(bookProgress: Long, trackProgress: Long) {
        Timber.i("Updating progress in ${this.javaClass.simpleName}")
        book.value = book.value.copy(progress = bookProgress)
        track.value = track.value.copy(progress = trackProgress)
        tracks.replaceTrack(track.value)

        if (tracks.isNotEmpty() && chapters.isNotEmpty()) {
            val activeTrack = tracks.getActiveTrack()
            val chapter = chapters.filter {
                it.trackId.toInt() == activeTrack.id
            }.getChapterAt(activeTrack.progress)
            if (chapter != this.chapter.value) {
                listener?.onChapterChange(chapter)
                this.chapter.value = chapter
            }
        }
    }

    private fun MutableList<MediaItemTrack>.replaceTrack(track: MediaItemTrack) {
        if (BuildConfig.DEBUG && isEmpty()) {
            throw IllegalStateException("Cannot replace track for empty track list")
        }
        val trackIndex = this.indexOfFirst { it.id == track.id }
        if (BuildConfig.DEBUG && trackIndex == -1) {
            throw IllegalStateException("Cannot replace track which does not exist")
        }
        this.removeAt(trackIndex)
        this.add(trackIndex, track)
    }
}