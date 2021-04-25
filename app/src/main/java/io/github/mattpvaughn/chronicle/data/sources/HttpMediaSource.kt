package io.github.mattpvaughn.chronicle.data.sources

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import com.bumptech.glide.load.model.LazyHeaders
import com.tonyodev.fetch2.Request
import io.github.mattpvaughn.chronicle.data.ConnectionState
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.model.Chapter
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import retrofit2.http.Path

/** A [MediaSource] whose authoritative source of truth in accessed via HTTP requests */
abstract class HttpMediaSource(private val applicationContext: Context) :
    MediaSource(applicationContext) {
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

    /** Fetches the tracks in an album. Primarily used to update track progress */
    abstract suspend fun fetchTracksForBook(@Path("albumId") bookId: Int): List<MediaItemTrack>

//    /** Informs the server that a media session has begun */
//    abstract suspend fun sendMediaSessionStartCommand()

    /** The state of the connection to the server. Represented by a [ConnectionState] */
    abstract val connectionState: LiveData<ConnectionState>

    /** Makes a [Request] with the needed HTTP headers */
    abstract fun makeDownloadRequest(trackUrl: String, dest: Uri, bookTitle: String): Request

    /** Makes a [LazyHeaders] with the needed HTTP headers for auth */
    abstract fun makeGlideHeaders(): LazyHeaders

    /** Appends a relative path (i.e. [MediaItemTrack.media]) to the media source's base url */
    abstract fun toServerString(relativePathForResource: String): String

    /** Whether user has access to the remote server */
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
    abstract override fun makeThumbUri(thumb: String): Uri?

    /**
     * Refreshes auth tokens in the [dataSourceFactory] to ensure they are the most recent version
     * possible
     */
    abstract fun refreshAuth()

    /** Fetches a single book from the source */
    abstract suspend fun fetchBook(bookId: Int): Audiobook?

}