package io.github.mattpvaughn.chronicle.data.sources

import com.bumptech.glide.load.model.LazyHeaders
import com.tonyodev.fetch2.Request
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import okhttp3.ResponseBody

/** A [MediaSource] whose authoritative source of truth in accessed via HTTP requests */
interface HttpMediaSource : MediaSource {
    /**
     * Fetches information not included in [fetchTracks] which must be fetched one track at a time,
     * like chapter information
     */
    suspend fun fetchAdditionalTrackInfo(): MediaItemTrack

    /** Fetches a file stream associated with a URL on the server */
    suspend fun fetchStream(url: String): ResponseBody

    /** Updates the playback progress of a [MediaItemTrack] to the server */
    suspend fun updateProgress(mediaItemTrack: MediaItemTrack, playbackState: String)

    /** Informs the server that a media session has begun */
    suspend fun sendMediaSessionStartCommand()

    /** Return true if the media source can currently be accessed, false otherwise */
    suspend fun isReachable(): Boolean

    /** Makes a [Request] with the needed HTTP headers */
    fun makeDownloadRequest(trackUrl: String): Request

    /** Makes a [LazyHeaders] with the needed HTTP headers */
    fun makeGlideHeaders(): LazyHeaders

    /** Appends a relative path (i.e. [MediaItemTrack.media]) to the media source's base url */
    fun toServerString(relativePathForResource: String): String
}