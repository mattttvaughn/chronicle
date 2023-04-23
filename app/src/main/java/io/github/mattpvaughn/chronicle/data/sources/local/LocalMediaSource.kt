package io.github.mattpvaughn.chronicle.data.sources.local

import com.github.michaelbull.result.Result
import com.google.android.exoplayer2.upstream.DefaultDataSource
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import io.github.mattpvaughn.chronicle.data.sources.MediaSource

/** A [MediaSource] wrapping files on the local filesystem */
class LocalMediaSource : MediaSource {

    override val id: Long = MEDIA_SOURCE_ID_LOCAL

    companion object {
        const val MEDIA_SOURCE_ID_LOCAL: Long = 2L
    }

    // TODO: acquire the permissions needed somehow

    override val dataSourceFactory: DefaultDataSource.Factory
        get() = TODO("Not yet implemented")

    override suspend fun fetchAudiobooks(): Result<List<Audiobook>, Throwable> {
        TODO("Not yet implemented")
    }

    override suspend fun fetchTracks(): Result<List<MediaItemTrack>, Throwable> {
        TODO("Not yet implemented")
    }

    override val isDownloadable: Boolean = false
}
