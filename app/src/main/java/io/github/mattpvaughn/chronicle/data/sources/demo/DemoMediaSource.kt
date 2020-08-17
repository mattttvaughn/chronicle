package io.github.mattpvaughn.chronicle.data.sources.demo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.android.exoplayer2.upstream.AssetDataSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DataSpec
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import io.github.mattpvaughn.chronicle.data.sources.MediaSource
import io.github.mattpvaughn.chronicle.navigation.Navigator
import timber.log.Timber
import java.io.IOException
import kotlin.time.ExperimentalTime
import kotlin.time.seconds


class DemoMediaSource(
    override val id: Long,
    private val applicationContext: Context
) : MediaSource(applicationContext) {

    private val draculaAudioAssetPath = "assets:///dracula_short.mp3"
    private val draculaArtAssetPath = "assets:///cover_small.jpg"

    override val name: String
        get() = "Demo Library"

    override val icon: Int
        get() = R.drawable.ic_library_music

    private val assetDataSource = AssetDataSource(applicationContext).apply {
        val dataSpec = DataSpec(Uri.parse(draculaAudioAssetPath))
        try {
            open(dataSpec)
        } catch (e: AssetDataSource.AssetDataSourceException) {
            Timber.e(e)
        }
    }

    override val dataSourceFactory: DataSource.Factory = DataSource.Factory { assetDataSource }

    @ExperimentalTime
    private val audiobooks = listOf(
        Audiobook(
            id = 1,
            source = id,
            title = "Dracula (first minute)",
            titleSort = "Dracula (first minute)",
            author = "Bram Stoker",
            duration = 75.seconds.toLongMilliseconds(),
            isCached = true,
            thumb = draculaArtAssetPath
        )
    )

    @ExperimentalTime
    private val tracks = listOf(
        // sole track for demo book 1
        MediaItemTrack(
            id = 2,
            parentKey = 1,
            title = "Intro",
            playQueueItemID = 1,
            index = 1,
            duration = 75.seconds.toLongMilliseconds(),
            album = "Dracula (first minute)",
            artist = "Bram Stoker",
            cached = true
        )
    )

    @ExperimentalTime
    override suspend fun fetchBooks(): Result<List<Audiobook>> {
        return Result.success(audiobooks)
    }

    @ExperimentalTime
    override suspend fun fetchTracks(): Result<List<MediaItemTrack>> {
        return Result.success(tracks)
    }


    /** No setup to do, just brings the user home */
    override fun setup(navigator: Navigator) {
        navigator.showHome()
    }

    override fun type(): String {
        return TAG
    }

    override fun makeThumbUri(src: String): Uri? {
        return Uri.parse(draculaArtAssetPath)
    }

    override fun getBitmapForThumb(uri: Uri): Bitmap? {
        Timber.i("Getting demo bitmap")
        return try {
            val stream = applicationContext.assets.open(draculaArtAssetPath)
            BitmapFactory.decodeStream(stream)
        } catch (e: IOException) {
            // handle exception
            Timber.i("Failed to get demo bitmap")
            null
        }
    }

    // false as it's already on device
    override val isDownloadable: Boolean
        get() = false

    companion object {
        const val TAG = "DemoMediaSource"
    }
}