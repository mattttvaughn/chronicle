package io.github.mattpvaughn.chronicle.data.sources.local

import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import io.github.mattpvaughn.chronicle.application.ChronicleApplication
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import io.github.mattpvaughn.chronicle.data.sources.MediaSource
import io.github.mattpvaughn.chronicle.navigation.Navigator

/** A [MediaSource] wrapping files on the local filesystem */
class LocalMediaSource(application: ChronicleApplication) : MediaSource(application) {

    override val id: Long = MEDIA_SOURCE_ID_LOCAL

    override val name: String
        get() = TODO("Not yet implemented")

    override val icon: Int
        get() = TODO("Not yet implemented")

    companion object {
        const val MEDIA_SOURCE_ID_LOCAL: Long = 2L
    }

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

    /**
     * Allows the user to choose the require directory and secures the permissions required to
     * access it
     */
    override fun setup(navigator: Navigator) {
        TODO("Not yet implemented")
    }

    override fun type(): String {
        TODO("Not yet implemented")
    }

    override val isDownloadable: Boolean = false

}