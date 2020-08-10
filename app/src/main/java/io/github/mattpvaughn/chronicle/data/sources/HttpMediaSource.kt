package io.github.mattpvaughn.chronicle.data.sources

import android.app.DownloadManager
import android.net.Uri
import androidx.lifecycle.LiveData
import com.bumptech.glide.load.model.LazyHeaders
import io.github.mattpvaughn.chronicle.application.ChronicleApplication
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.model.Chapter
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack

/** A [MediaSource] whose authoritative source of truth in accessed via HTTP requests */
abstract class HttpMediaSource(application: ChronicleApplication) : MediaSource(application) {
    /**
     * Fetches information no provided by [fetchTracks] which must be fetched one track at a time,
     * like chapter information
     */
    abstract suspend fun fetchChapterInfo(
        isAudiobookCached: Boolean,
        tracks: List<MediaItemTrack>
    ): List<Chapter>

//    /** Fetches a file stream associated with a URL on the server */
//    abstract suspend fun fetchStream(url: String): ResponseBody

    /** Updates the playback progress of a [MediaItemTrack] to the server */
    abstract suspend fun updateProgress(
        trackId: String,
        trackProgress: String,
        key: String,
        duration: Long,
        playState: String,
        hasMde: Int,
        playbackTime: Long = 0L,
        playQueueItemId: Long = 0L
    )

//    /** Informs the server that a media session has begun */
//    abstract suspend fun sendMediaSessionStartCommand()

    /** Return true if the media source can currently be accessed, false otherwise */
    abstract fun isReachable(): Boolean

    /** Makes a [DownloadManager.Request] with the needed HTTP headers */
    abstract fun makeDownloadRequest(trackUrl: String): DownloadManager.Request

    /** Makes a [LazyHeaders] with the needed HTTP headers for auth */
    abstract fun makeGlideHeaders(): LazyHeaders

    /** Appends a relative path (i.e. [MediaItemTrack.media]) to the media source's base url */
    abstract fun toServerString(relativePathForResource: String): String

    /** Whether user has access to the content */
    abstract fun isAuthorized(): Boolean

    abstract fun isAuthorizedObservable(): LiveData<Boolean>

    /** Inform server that the track has been finished */
    abstract suspend fun watched(mediaKey: Int)

    /** Attempt to connect source to remote http server */
    abstract suspend fun connectToRemote()

    /** Inform source that the connection has been lost */
    abstract fun connectionHasBeenLost()

    /**
     * Given a relative path to a thumb as stored in [Audiobook.thumb], prepend with server url and
     * add auth if needed
     */
    abstract override fun makeThumbUri(src: String): Uri?
}