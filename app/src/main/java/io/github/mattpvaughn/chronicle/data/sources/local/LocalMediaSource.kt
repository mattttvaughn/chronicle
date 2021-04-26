package io.github.mattpvaughn.chronicle.data.sources.local

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import coil.Coil
import coil.request.ImageRequest
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.FileDataSource
import com.google.android.exoplayer2.util.Util
import io.github.mattpvaughn.chronicle.CoroutineDispatchers
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.application.ChronicleApplication
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import io.github.mattpvaughn.chronicle.data.sources.MediaSource
import io.github.mattpvaughn.chronicle.navigation.Navigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import okio.IOException

/** A [MediaSource] wrapping files on the local filesystem */
class LocalMediaSource(
    override val id: Long,
    val dispatchers: CoroutineDispatchers = CoroutineDispatchers(),
    private val application: ChronicleApplication
) : MediaSource(application), CoroutineScope {

    override val coroutineContext = SupervisorJob() + Dispatchers.Default

    override val name: String
        get() = DocumentFile.fromTreeUri(
            application.applicationContext,
            Uri.parse(localPrefs.root)
        )?.name.takeIf { !it.isNullOrEmpty() } ?: "Local library"

    override val icon: Int
        get() = R.drawable.ic_folder_white

    private val playerInfo = Util.getUserAgent(application.applicationContext, "ExoPlayerInfo")
    override val dataSourceFactory: DefaultDataSourceFactory
        get() = DefaultDataSourceFactory(application.applicationContext) {
            FileDataSource()
        }

    private val localPrefs: LocalLibraryPrefs = SharedPreferencesLocalLibrary(this)

    /**
     * Fetches books from the directory selected by user, and ensures the app has permissions to
     * access it
     */
    override suspend fun fetchBooks(): Result<List<Audiobook>> {
        return Result.success(emptyList())
    }

    private fun String.toTitleSort(): String {
        TODO("Implement me")
    }

    /**
     * Fetches tracks from the directory selected by user, and ensures the app has permissions to
     * access it
     */
    override suspend fun fetchTracks(): Result<List<MediaItemTrack>> {
        // do a permissions check, if it fails
        val rootUri = Uri.parse(localPrefs.root)
        val root = DocumentFile.fromTreeUri(application.applicationContext, rootUri)
            ?: return Result.failure(IOException("Root folder could not make DocumentFile"))
        val localMediaParser = LocalMediaParser(application)
        val tracks = localMediaParser.parseMedia(id, root)
        return Result.success(tracks)
    }

    /** Allows the user to choose the source directory and secures permissions */
    override fun setup(navigator: Navigator) {}

    override fun type() = TAG

    override fun getThumbBuilder() = ImageRequest.Builder(application.applicationContext)

    override suspend fun getBitmapForThumb(uri: Uri): Bitmap? {
        val req = ImageRequest.Builder(application.applicationContext)
            .data(uri)
            .build()

        return withContext(Dispatchers.IO) {
            val res = Coil.imageLoader(application.applicationContext)
                .execute(req)
            (res.drawable as BitmapDrawable).bitmap
        }
    }

    override fun makeThumbUri(src: String): Uri? {
        return Uri.parse(src)
    }

    override fun getTrackSource(track: MediaItemTrack): Uri? {
        return Uri.parse(track.media)
    }

    fun setRoot(uri: Uri) {
        localPrefs.root = uri.toString()
    }

    override val isDownloadable: Boolean = false

    companion object {
        const val TAG = "LocalLibrarySource"
    }

}