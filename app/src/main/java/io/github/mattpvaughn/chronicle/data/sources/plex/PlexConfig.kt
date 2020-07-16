package io.github.mattpvaughn.chronicle.data.sources.plex

import android.app.DownloadManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import io.github.mattpvaughn.chronicle.BuildConfig
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexConfig.ConnectionResult.Failure
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexConfig.ConnectionResult.Success
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexConfig.ConnectionState.*
import io.github.mattpvaughn.chronicle.data.sources.plex.model.Connection
import io.github.mattpvaughn.chronicle.views.GlideUrlRelativeCacheKey
import kotlinx.coroutines.*
import timber.log.Timber
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Responsible for the configuration of the Plex.
 *
 * Eventually will provide the sole interface for interacting with the Plex remote source.
 *
 * TODO: merge the behavior here into [PlexMediaSource]
 */
@Singleton
class PlexConfig @Inject constructor(private val plexPrefsRepo: PlexPrefsRepo) {

    private val connectionSet = mutableSetOf<Connection>()

    var url: String = PLACEHOLDER_URL

    private val _isConnected = MutableLiveData(false)
    val isConnected: LiveData<Boolean>
        get() = _isConnected

    private val _connectionState = object : MutableLiveData<ConnectionState>(NOT_CONNECTED) {
        override fun postValue(value: ConnectionState?) {
            _isConnected.postValue(value == CONNECTED)
            super.postValue(value)
        }

        override fun setValue(value: ConnectionState?) {
            _isConnected.postValue(value == CONNECTED)
            super.setValue(value)
        }
    }
    val connectionState: LiveData<ConnectionState>
        get() = _connectionState

    enum class ConnectionState {
        CONNECTING,
        NOT_CONNECTED,
        CONNECTED,
        CONNECTION_FAILED
    }

    val sessionIdentifier = Random.nextInt(until = 10000).toString()

    fun toServerString(relativePath: String): String {
        val baseEndsWith = url.endsWith('/')
        val pathStartsWith = relativePath.startsWith('/')
        return if (baseEndsWith && pathStartsWith) {
            "$url/${relativePath.substring(1)}"
        } else if (!baseEndsWith && !pathStartsWith) {
            "$url/$relativePath"
        } else {
            "$url$relativePath"
        }
    }

    val plexMediaInterceptor = PlexInterceptor(plexPrefsRepo, this, isLoginService = false)
    val plexLoginInterceptor = PlexInterceptor(plexPrefsRepo, this, isLoginService = true)

    /** Attempt to load in a cached bitmap for the given thumbnail */
    suspend fun getBitmapFromServer(thumb: String?, requireCached: Boolean = false): Bitmap? {
        if (thumb.isNullOrEmpty()) {
            return null
        }

        // Retrieve cached album art from Glide if available
        val appContext = Injector.get().applicationContext()
        val imageSize = appContext.resources.getDimension(R.dimen.audiobook_image_width).toInt()
        val url = URL(
            if (thumb.startsWith("http")) {
                thumb
            } else {
                toServerString("photo/:/transcode?width=$imageSize&height=$imageSize&url=$thumb")
            }
        )
        Timber.i("Url is: $url")
        val glideUrl = GlideUrlRelativeCacheKey(url, makeGlideHeaders())
        return try {
            return withContext(Dispatchers.IO) {
                val bm = Glide.with(appContext)
                    .asBitmap()
                    .load(glideUrl)
                    .onlyRetrieveFromCache(requireCached)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .timeout(200)
                    .transform(CenterCrop())
                    .submit()
                    .get()
                Timber.i("Successfully retrieved album art for $thumb")
                bm
            }
        } catch (t: Throwable) {
            // who cares?
            Timber.e("Failed to retrieve album art for $thumb: $t")
            null
        }
    }

    fun makeGlideHeaders(): LazyHeaders {
        return LazyHeaders.Builder()
            .addHeader(
                "X-Plex-Token",
                plexPrefsRepo.server?.accessToken ?: plexPrefsRepo.accountAuthToken
            )
            .build()
    }

    fun makeDownloadRequest(trackSource: String): DownloadManager.Request {
        Timber.i("Preparing download request for: ${Uri.parse(toServerString(trackSource))}")
        return DownloadManager.Request(Uri.parse(toServerString(trackSource)))
            .addRequestHeader("X-Plex-Platform", "Android")
            .addRequestHeader("X-Plex-Provides", "player,timeline")
            .addRequestHeader("X-Plex-Client-Name", APP_NAME)
            .addRequestHeader("X-Plex-Client-Identifier", plexPrefsRepo.uuid)
            .addRequestHeader("X-Plex-Version", BuildConfig.VERSION_NAME)
            .addRequestHeader("X-Plex-Product", APP_NAME)
            .addRequestHeader("X-Plex-Platform-Version", Build.VERSION.RELEASE)
            .addRequestHeader("X-Plex-Device", Build.MODEL)
            .addRequestHeader("X-Plex-Device-Name", Build.MODEL)
            .addRequestHeader("X-Plex-Session-Identifier", sessionIdentifier)
            .addRequestHeader(
                "X-Plex-Token",
                plexPrefsRepo.server?.accessToken ?: plexPrefsRepo.accountAuthToken
            )
    }

    fun makeUriFromPart(part: String): Uri {
        val uri = Uri.parse(toServerString(part))
        return uri.buildUpon()
            .appendQueryParameter("X-Plex-Platform", "Android")
            .appendQueryParameter("X-Plex-Provides", "player,timeline")
            .appendQueryParameter("X-Plex-Client-Name", APP_NAME)
            .appendQueryParameter("X-Plex-Client-Identifier", plexPrefsRepo.uuid)
            .appendQueryParameter("X-Plex-Version", BuildConfig.VERSION_NAME)
            .appendQueryParameter("X-Plex-Product", APP_NAME)
            .appendQueryParameter("X-Plex-Platform-Version", Build.VERSION.RELEASE)
            .appendQueryParameter("X-Plex-Device", Build.MODEL)
            .appendQueryParameter("X-Plex-Device-Name", Build.MODEL)
            .appendQueryParameter("X-Plex-Session-Identifier", sessionIdentifier)
            .appendQueryParameter(
                "X-Plex-Token",
                plexPrefsRepo.server?.accessToken ?: plexPrefsRepo.accountAuthToken
            ).build()
    }

    fun setPotentialConnections(connections: List<Connection>) {
        connectionSet.clear()
        connectionSet.addAll(connections)
    }

    /**
     * Indicates to observers that connectivity has been lost, but does not update URL yet, as
     * querying a possibly dead url has a better chance of success than querying no url
     */
    fun connectionHasBeenLost() {
        _connectionState.value = NOT_CONNECTED
    }


    private var prevConnectToServerJob: CompletableJob? = null

    @InternalCoroutinesApi
    fun connectToServer(plexMediaService: PlexMediaService) {
        prevConnectToServerJob?.cancel("Killing previous connection attempt")
        _connectionState.postValue(CONNECTING)
        prevConnectToServerJob = Job().also {
            val context = CoroutineScope(it + Dispatchers.Main)
            context.launch {
                val connectionResult = chooseViableConnections(plexMediaService)
                Timber.i("Returned connection $connectionResult")
                if (connectionResult is Success && connectionResult.url != PLACEHOLDER_URL) {
                    url = connectionResult.url
                    _connectionState.postValue(CONNECTED)
                    Timber.i("Connection success: $url")
                } else {
                    _connectionState.postValue(CONNECTION_FAILED)
                }
            }
        }
    }

    /** Clear server data from [plexPrefsRepo] and [url] managed by [PlexConfig] */
    fun clear() {
        plexPrefsRepo.clear()
        _connectionState.postValue(NOT_CONNECTED)
        url = PLACEHOLDER_URL
        connectionSet.clear()
    }

    fun clearServer() {
        _connectionState.postValue(NOT_CONNECTED)
        url = PLACEHOLDER_URL
        plexPrefsRepo.server = null
        plexPrefsRepo.library = null
    }

    fun clearLibrary() {
        plexPrefsRepo.library = null
    }

    fun clearUser() {
        plexPrefsRepo.library = null
        plexPrefsRepo.server = null
        plexPrefsRepo.user = null
    }

    sealed class ConnectionResult {
        data class Success(val url: String) : ConnectionResult()
        object Failure : ConnectionResult()
    }

    /**
     * Attempts to connect to all [Connection]s in [connectionSet] via [PlexMediaService.checkServer].
     *
     * On the first successful connection, return a [ConnectionResult.Success] with
     *   [ConnectionResult.Success.url] from the [Connection.uri]
     *
     * If all connections fail: return a [Failure] as soon as all connections have completed
     *
     * If no connections are made within 15 seconds, return a [ConnectionResult.Failure].
     */
    @InternalCoroutinesApi
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun chooseViableConnections(plexMediaService: PlexMediaService): ConnectionResult {
        return withTimeoutOrNull(15000) {
            Timber.i("Choosing viable connection from: $connectionSet")
            val connections = connectionSet.sortedByDescending { it.local }
            val deferredConnections = connections.map { conn ->
                async {
                    Timber.i("Testing connection: ${conn.uri}")
                    try {
                        plexMediaService.checkServer(conn.uri)
                        return@async Success(conn.uri)
                    } catch (e: Throwable) {
                        return@async Failure
                    }
                }
            }

            while (deferredConnections.any { it.isActive }) {
                Timber.i("Connections: $deferredConnections")
                deferredConnections.forEach { deferred ->
                    if (deferred.isCompleted) {
                        val completed = deferred.getCompleted()
                        if (completed is Success) {
                            Timber.i("Returning connection $completed")
                            deferredConnections.forEach { it.cancel("Sibling completed, killing connection attempt: $it") }
                            return@withTimeoutOrNull completed
                        }
                    }
                }
                delay(500)
            }

            // Check if the final completed job was a success
            Timber.i("Connections: $deferredConnections")
            deferredConnections.forEach { deferred ->
                if (deferred.isCompleted && deferred.getCompleted() is Success) {
                    Timber.i("Returning final completed connection ${deferred.getCompleted()}")
                    return@withTimeoutOrNull deferred.getCompleted()
                }
            }

            Timber.i("Returning connection $Failure")
            Failure
        } ?: Failure
    }
}