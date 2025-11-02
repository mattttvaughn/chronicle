package io.github.mattpvaughn.chronicle.data.sources

import androidx.media3.datasource.DefaultDataSource
import com.github.michaelbull.result.Result
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack

interface MediaSource {
    /** An ID uniquely representing a specific source. */
    val id: Long

    /**
     * Expose a [DefaultDataSource.Factory] which can transform a [List<MediaItemTrack>] into a
     * [androidx.media3.exoplayer.source.ConcatenatingMediaSource]
     */
    val dataSourceFactory: DefaultDataSource.Factory

    /** Fetch all audiobooks */
    suspend fun fetchAudiobooks(): Result<List<Audiobook>, Throwable>

    /** Fetch all tracks */
    suspend fun fetchTracks(): Result<List<MediaItemTrack>, Throwable>

    /**
     * Whether books provided by the source can be downloaded. For example, we could consider
     * local files to not be downloadable, while files provided by a server would be
     */
    val isDownloadable: Boolean

    companion object {
        const val NO_SOURCE_FOUND = -1L
    }
}
