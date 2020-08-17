package io.github.mattpvaughn.chronicle.data.sources.plex

import android.app.DownloadManager
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import com.squareup.moshi.Moshi
import io.github.mattpvaughn.chronicle.BuildConfig
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.data.APP_NAME
import io.github.mattpvaughn.chronicle.data.ConnectionState
import io.github.mattpvaughn.chronicle.data.ConnectionState.*
import io.github.mattpvaughn.chronicle.data.model.*
import io.github.mattpvaughn.chronicle.data.sources.HttpMediaSource
import io.github.mattpvaughn.chronicle.data.sources.MediaSource
import io.github.mattpvaughn.chronicle.data.sources.plex.IPlexLoginRepo.LoginState.LOGGED_IN_NO_SERVER_CHOSEN
import io.github.mattpvaughn.chronicle.data.sources.plex.IPlexLoginRepo.LoginState.LOGGED_IN_NO_USER_CHOSEN
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexLibrarySource.ConnectionResult.Failure
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexLibrarySource.ConnectionResult.Success
import io.github.mattpvaughn.chronicle.data.sources.plex.model.*
import io.github.mattpvaughn.chronicle.navigation.Navigator
import io.github.mattpvaughn.chronicle.views.GlideUrlRelativeCacheKey
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.internal.userAgent
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import timber.log.Timber
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** A [MediaSource] responsible for interacting with a remote Plex library */
class PlexLibrarySource constructor(
    override val id: Long,
    applicationContext: Context,
    loggingInterceptor: HttpLoggingInterceptor,
    moshi: Moshi
) : HttpMediaSource(applicationContext) {

    private val connectionSet = mutableSetOf<Connection>()

    var url: String = PLACEHOLDER_URL

    private val _connectionState = MutableLiveData<ConnectionState>(NOT_CONNECTED)
    override val connectionState: LiveData<ConnectionState>
        get() = _connectionState

    val sessionIdentifier = Random.nextInt(until = 10000).toString()

    /** Prepends the current server url to [relativePathForResource], accounting for trailing/leading `/`s */
    override fun toServerString(relativePathForResource: String): String {
        val baseEndsWith = url.endsWith('/')
        val pathStartsWith = relativePathForResource.startsWith('/')
        return if (baseEndsWith && pathStartsWith) {
            "$url/${relativePathForResource.substring(1)}"
        } else if (!baseEndsWith && !pathStartsWith) {
            "$url/$relativePathForResource"
        } else {
            "$url$relativePathForResource"
        }
    }

    override val name: String
        get() = plexPrefsRepo.library?.name.takeIf { !it.isNullOrEmpty() } ?: "Plex library"

    override val icon: Int
        get() = R.drawable.ic_library_music

    private val plexPrefsRepo: PlexPrefsRepo = SharedPreferencesPlexPrefsRepo(
        applicationContext.getSharedPreferences(id.toString(), MODE_PRIVATE),
        moshi
    )

    private val defaultHttpFactory = DefaultHttpDataSourceFactory(userAgent).also {
        val props = it.defaultRequestProperties
        props.set("X-Plex-Platform", "Android")
        props.set("X-Plex-Provides", "player")
        props.set(
            "X-Plex_Client-Name",
            APP_NAME
        )
        props.set("X-Plex-Client-Identifier", plexPrefsRepo.uuid)
        props.set("X-Plex-Version", BuildConfig.VERSION_NAME)
        props.set(
            "X-Plex-Product",
            APP_NAME
        )
        props.set("X-Plex-Platform-Version", Build.VERSION.RELEASE)
        props.set("X-Plex-Device", Build.MODEL)
        props.set("X-Plex-Device-Name", Build.MODEL)
        props.set(
            "X-Plex-Token",
            plexPrefsRepo.server?.accessToken ?: plexPrefsRepo.accountAuthToken
        )
    }

    // Handles http and local files
    override val dataSourceFactory: DefaultDataSourceFactory =
        DefaultDataSourceFactory(Injector.get().applicationContext(), defaultHttpFactory)


    private val plexMediaInterceptor = PlexInterceptor(plexPrefsRepo, this, isLoginService = false)
    private val plexLoginInterceptor = PlexInterceptor(plexPrefsRepo, this, isLoginService = true)

    private val mediaOkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(plexMediaInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()

    private val loginOkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(plexLoginInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()

    private val mediaRetrofit: Retrofit = Retrofit.Builder()
        .addConverterFactory(MoshiConverterFactory.create())
        .client(mediaOkHttpClient)
        .baseUrl(PLACEHOLDER_URL) // this will be replaced by PlexInterceptor as needed
        .build()

    private val loginRetrofit: Retrofit = Retrofit.Builder()
        .addConverterFactory(MoshiConverterFactory.create())
        .client(loginOkHttpClient)
        .baseUrl(PLACEHOLDER_URL) // this will be replaced by PlexInterceptor as needed
        .build()

    private val mediaService: PlexMediaService = mediaRetrofit.create(PlexMediaService::class.java)
    private val loginService: PlexLoginService = loginRetrofit.create(PlexLoginService::class.java)

    private val plexLoginRepo: PlexLoginRepo = PlexLoginRepo(plexPrefsRepo, loginService)

    override suspend fun fetchBooks(): Result<List<Audiobook>> {
        return try {
            val libraryId = plexPrefsRepo.library?.id!!
            val response = mediaService.retrieveAllAlbums(libraryId)
            Result.success(response.plexMediaContainer.asAudiobooks(id))
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    override suspend fun fetchTracks(): Result<List<MediaItemTrack>> {
        return try {
            val response = mediaService.retrieveAllTracksInLibrary(plexPrefsRepo.library?.id!!)
            Result.success(response.plexMediaContainer.asTrackList(id))
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /** Launches Plex OAuth login process up to choosing library */
    override fun setup(navigator: Navigator) {
        TODO("Not yet implemented")
    }

    override fun type(): String {
        return TAG
    }

    override fun getBitmapForThumb(uri: Uri): Bitmap? {
        TODO("Not yet implemented")
    }

    override val isDownloadable: Boolean = true

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
                Timber.i("Taking part uri")
                toServerString("photo/:/transcode?width=$imageSize&height=$imageSize&url=$thumb")
            }
        )
        Timber.i("Notification thumb uri is: $url")
        val glideUrl = GlideUrlRelativeCacheKey(url, makeGlideHeaders())
        return try {
            return withContext(Dispatchers.IO) {
                val bm = Glide.with(appContext)
                    .asBitmap()
                    .load(glideUrl)
                    .onlyRetrieveFromCache(requireCached)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
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

    override fun makeGlideHeaders(): LazyHeaders {
        return LazyHeaders.Builder()
            .addHeader(
                "X-Plex-Token",
                plexPrefsRepo.server?.accessToken ?: plexPrefsRepo.accountAuthToken
            )
            .build()
    }

    override suspend fun fetchChapterInfo(
        isAudiobookCached: Boolean,
        tracks: List<MediaItemTrack>
    ): List<Chapter> {
        return tracks.flatMap {
            mediaService.retrieveChapterInfo(it.id).plexMediaContainer.metadata
                .firstOrNull()
                ?.plexChapters
                // TODO: below won't work for books made of multiple m4b files
                ?.map { plexChapter -> plexChapter.toChapter(isAudiobookCached) }
                ?: emptyList()
        }
    }

    suspend fun fetchLibraries(): PlexMediaContainerWrapper {
        return mediaService.retrieveLibraries()
    }

    suspend fun fetchServers(): List<PlexServer> {
        return loginService.resources()
    }

    suspend fun fetchUsersForAccount(): UsersResponse {
        return loginService.getUsersForAccount()
    }

    suspend fun pickUser(uuid: String, pin: String): Result<PlexUser> {
        return try {
            val user = loginService.pickUser(uuid, pin)
            Result.success(user)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    override suspend fun updateProgress(
        trackId: String,
        trackProgress: String,
        key: String,
        duration: Long,
        playState: String,
        hasMde: Int,
        playbackTime: Long,
        playQueueItemId: Long
    ) {
        mediaService.progress(
            ratingKey = trackId,
            offset = trackProgress,
            playbackTime = playbackTime,
            playQueueItemId = playQueueItemId,
            key = "${MediaItemTrack.PARENT_KEY_PREFIX}$trackId",
            duration = duration,
            playState = playState,
            hasMde = 1
        )
    }

    override suspend fun fetchTracksForBook(bookId: Int): List<MediaItemTrack> {
        return mediaService.retrieveTracksForAlbum(bookId).plexMediaContainer.asTrackList(id)
    }

//    override suspend fun sendMediaSessionStartCommand() {
//        TODO("Not yet implemented")
//    }

    override fun makeDownloadRequest(trackUrl: String): DownloadManager.Request {
        Timber.i("Preparing download request for: ${Uri.parse(toServerString(trackUrl))}")
        return DownloadManager.Request(Uri.parse(toServerString(trackUrl)))
            .addRequestHeader("X-Plex-Platform", "Android")
            .addRequestHeader("X-Plex-Provides", "player,timeline")
            .addRequestHeader(
                "X-Plex-Client-Name",
                APP_NAME
            )
            .addRequestHeader("X-Plex-Client-Identifier", plexPrefsRepo.uuid)
            .addRequestHeader("X-Plex-Version", BuildConfig.VERSION_NAME)
            .addRequestHeader(
                "X-Plex-Product",
                APP_NAME
            )
            .addRequestHeader("X-Plex-Platform-Version", Build.VERSION.RELEASE)
            .addRequestHeader("X-Plex-Device", Build.MODEL)
            .addRequestHeader("X-Plex-Device-Name", Build.MODEL)
            .addRequestHeader("X-Plex-Session-Identifier", sessionIdentifier)
            .addRequestHeader(
                "X-Plex-Token",
                plexPrefsRepo.server?.accessToken ?: plexPrefsRepo.accountAuthToken
            )
    }

    override fun makeThumbUri(thumb: String): Uri {
        val appContext = Injector.get().applicationContext()
        val imageSize = appContext.resources.getDimension(R.dimen.audiobook_image_width).toInt()
        val plexThumbPart = "photo/:/transcode?width=$imageSize&height=$imageSize&url=$thumb"
        val uri = Uri.parse(toServerString(plexThumbPart))
        return uri.buildUpon()
            .appendQueryParameter(
                "X-Plex-Token",
                plexPrefsRepo.server?.accessToken ?: plexPrefsRepo.accountAuthToken
            ).build()
    }

    /**
     * Indicates to observers that connectivity has been lost, but does not update URL yet, as
     * querying a possibly dead url has a better chance of success than querying no url
     */
    override fun connectionHasBeenLost() {
        _connectionState.value = NOT_CONNECTED
    }

    private var prevConnectToServerJob: CompletableJob? = null

    @InternalCoroutinesApi
    override suspend fun connectToRemote() {
        connectionSet.clear()
        connectionSet.addAll(plexPrefsRepo.server?.connections ?: emptyList())
        prevConnectToServerJob?.cancel("Killing previous connection attempt")
        _connectionState.postValue(CONNECTING)
        prevConnectToServerJob = Job().also {
            val context = CoroutineScope(it + Dispatchers.Main)
            context.launch {
                val connectionResult = chooseViableConnections(mediaService)
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

    /** Clear server data from [plexPrefsRepo] and [url] managed by [PlexLibrarySource] */
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

    fun chooseServer(serverModel: ServerModel) {
        connectionSet.clear()
        connectionSet.addAll(serverModel.connections)
        plexLoginRepo.chooseServer(serverModel)
    }

    fun chooseUser(user: PlexUser) {
        plexLoginRepo.chooseUser(user)
    }

    fun chooseLibrary(plexLibrary: PlexLibrary) {
        plexLoginRepo.chooseLibrary(plexLibrary)
    }

    override fun isAuthorized(): Boolean {
        return plexPrefsRepo.server?.accessToken != null && plexPrefsRepo.library != null
    }

    private var isAuthorizedLiveData = MutableLiveData<Boolean>(false)
    override fun isAuthorizedObservable(): LiveData<Boolean> {
        return isAuthorizedLiveData
    }

    override suspend fun watched(mediaKey: Int) {
        mediaService.watched(mediaKey.toString())
    }

    suspend fun startMediaSession(bookId: String) {
        val serverId = plexPrefsRepo.server?.serverId ?: ""
        val serverUri = getMediaItemUri(serverId, bookId)
        mediaService.startMediaSession(serverUri)
    }

    suspend fun postOAuthPin(): OAuthResponse? {
        return plexLoginRepo.postOAuthPin()
    }

    fun makeOAuthUrl(id: String, code: String): Uri {
        return plexLoginRepo.makeOAuthUrl(id, code)
    }

    suspend fun checkForOAuthAccessToken(navigator: Navigator): Result<IPlexLoginRepo.LoginState> {
        val result = plexLoginRepo.checkForOAuthAccessToken()
        if (result.isFailure) {
            return result
        }
        when (result.getOrNull()) {
            LOGGED_IN_NO_USER_CHOSEN -> {
                // multiple users available- so show user chooser
                navigator.showUserChooser()
            }
            LOGGED_IN_NO_SERVER_CHOSEN -> {
                // only one user was available, so skipping directly to server picker
                navigator.showServerChooser()
            }
            else -> {
                return Result.failure(IllegalStateException("Impossible network state: neither NO_USERS_CHOSEN nor NO_SERVER_CHOSEN"))
            }
        }
        return Result.success(result.getOrElse { IPlexLoginRepo.LoginState.FAILED_TO_LOG_IN })
    }

    companion object {
        const val TAG = "PlexLibrarySource"

        /**
         * Creates a URI uniquely identifying a media item with id [mediaId] on a server with machine
         * identifier [machineIdentifier]
         */
        fun getMediaItemUri(machineIdentifier: String, mediaId: String): String {
            return "server://$machineIdentifier/com.plexapp.plugins.library/library/metadata/$mediaId"
        }

    }

}