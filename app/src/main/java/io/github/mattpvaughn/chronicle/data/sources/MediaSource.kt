package io.github.mattpvaughn.chronicle.data.sources

import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.net.Uri
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import io.github.mattpvaughn.chronicle.application.ChronicleApplication
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import io.github.mattpvaughn.chronicle.features.home.HomeFragment
import io.github.mattpvaughn.chronicle.navigation.Navigator

/**
 * A source which provides [Audiobook]s and [MediaItemTrack]s. May require setup via [setup] before
 * media can be accessed. Each source is responsible for maintaining its own persistent state
 */
abstract class MediaSource(private val application: ChronicleApplication) {

    /** An ID uniquely representing a specific source. */
    abstract val id: Long

    /** The name of the source. Uniqueness not required */
    abstract val name: String

    /** An icon representing the source type */
    abstract val icon: Int

    fun prefs(): SharedPreferences {
        return application.getSharedPreferences(id.toString(), MODE_PRIVATE)
    }

    /**
     * Expose a [DefaultDataSourceFactory] which can transform a [List<MediaItemTrack>] into a
     * [com.google.android.exoplayer2.source.ConcatenatingMediaSource]
     */
    abstract val dataSourceFactory: DefaultDataSourceFactory

    /** Fetch all audiobooks */
    abstract suspend fun fetchBooks(): Result<List<Audiobook>>

    /** Fetch all tracks */
    abstract suspend fun fetchTracks(): Result<List<MediaItemTrack>>

    /**
     * Sets up the the source to provide media. Upon completion of setup, the user should be
     * returned to the [HomeFragment]
     *
     * If no setup is required, simply returns the user to the [HomeFragment]
     */
    abstract fun setup(navigator: Navigator)

    /** All values which ought to be persisted for the source */
    abstract fun type(): String

    /** Creates a [Uri] from thumb src [Audiobook.thumb] */
    abstract fun makeThumbUri(src: String): Uri?

    /**
     * Whether books provided by the source can be downloaded. For example, we could consider
     * local files to not be downloadable, while files provided by a server would be downloadable
     */
    abstract val isDownloadable: Boolean

    companion object {
        const val NO_SOURCE_FOUND = -1L
    }
}
