package io.github.mattpvaughn.chronicle.data.sources.local

import android.graphics.Bitmap
import android.net.Uri
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.application.ChronicleApplication
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import io.github.mattpvaughn.chronicle.data.sources.MediaSource
import io.github.mattpvaughn.chronicle.navigation.Navigator

/** A [MediaSource] wrapping files on the local filesystem */
class LocalMediaSource(
    override val id: Long,
    application: ChronicleApplication
) : MediaSource(application) {

    override val name: String
        get() = TODO("Not yet implemented")

    override val icon: Int
        get() = R.drawable.ic_folder_white

    override val dataSourceFactory: DefaultDataSourceFactory
        get() = TODO("Not yet implemented")

    /**
     * Fetches books from the directory selected by user, and ensures the app has permissions to
     * access it
     */
    override suspend fun fetchBooks(): Result<List<Audiobook>> {
        TODO("Not yet implemented")
    }

    /**
     * Fetches tracks from the directory selected by user, and ensures the app has permissions to
     * access it
     */
    override suspend fun fetchTracks(): Result<List<MediaItemTrack>> {
        TODO("Not yet implemented")
    }

    /** Allows the user to choose the source directory and secures permissions */
    override fun setup(navigator: Navigator) {
        TODO("Not yet implemented")
    }

    override fun type(): String {
        TODO("Not yet implemented")
    }

    override fun makeThumbUri(src: String): Uri? {
        TODO("Not yet implemented")
    }

    override fun getBitmapForThumb(uri: Uri): Bitmap? {
        TODO("Not yet implemented")
    }

    override fun getTrackSource(track: MediaItemTrack): Uri? {
        TODO("Not yet implemented")
    }

    override val isDownloadable: Boolean = false

}