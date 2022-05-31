package io.github.mattpvaughn.chronicle.data.sources.plex

import android.content.Context
import com.github.michaelbull.result.Result
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import com.tonyodev.fetch2.Request
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import io.github.mattpvaughn.chronicle.data.sources.HttpMediaSource
import io.github.mattpvaughn.chronicle.data.sources.MediaSource
import okhttp3.ResponseBody
import javax.inject.Inject

/** A [MediaSource] wrapping Plex media server and its media calls via audio libraries */
class PlexMediaSource @Inject constructor(
    private val plexConfig: PlexConfig,
    private val plexMediaService: PlexMediaService,
    private val plexLoginRepo: IPlexLoginRepo,
    private val appContext: Context,
    defaultDataSourceFactory: DefaultHttpDataSourceFactory
) : HttpMediaSource {

    override val id: Long = MEDIA_SOURCE_ID_PLEX

    companion object {
        const val MEDIA_SOURCE_ID_PLEX: Long = 0L
    }

    override val dataSourceFactory: DefaultDataSourceFactory = DefaultDataSourceFactory(appContext, defaultDataSourceFactory)

    override val isDownloadable: Boolean = true

    override suspend fun fetchAudiobooks(): Result<List<Audiobook>, Throwable> {
        TODO("Not yet implemented")
    }

    override suspend fun fetchTracks(): Result<List<MediaItemTrack>, Throwable> {
        TODO("Not yet implemented")
    }

    override suspend fun fetchAdditionalTrackInfo(): MediaItemTrack {
        TODO("Not yet implemented")
    }

    override suspend fun fetchStream(url: String): ResponseBody {
        TODO("Not yet implemented")
    }

    override suspend fun updateProgress(mediaItemTrack: MediaItemTrack, playbackState: String) {
        TODO("Not yet implemented")
    }

    override suspend fun sendMediaSessionStartCommand() {
        TODO("Not yet implemented")
    }

    override suspend fun isReachable(): Boolean {
        TODO("Not yet implemented")
    }

    override fun makeDownloadRequest(trackUrl: String): Request {
        TODO("Not yet implemented")
    }

    override fun makeGlideHeaders(): Object? {
        TODO("Not yet implemented")
    }

    override fun toServerString(relativePathForResource: String): String {
        TODO("Not yet implemented")
    }
}
