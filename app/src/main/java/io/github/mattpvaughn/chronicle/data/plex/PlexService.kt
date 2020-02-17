package io.github.mattpvaughn.chronicle.data.plex

import android.app.DownloadManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.bumptech.glide.load.model.LazyHeaders
import io.github.mattpvaughn.chronicle.BuildConfig
import io.github.mattpvaughn.chronicle.application.LOG_NETWORK_REQUESTS
import io.github.mattpvaughn.chronicle.data.plex.model.Connection
import io.github.mattpvaughn.chronicle.data.plex.model.MediaContainer
import io.github.mattpvaughn.chronicle.data.plex.model.MediaType
import io.github.mattpvaughn.chronicle.data.plex.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.simplexml.SimpleXmlConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit
import kotlin.random.Random


const val PLEX_LOGIN_SERVICE_URL = "https://plex.tv"

private val loggingInterceptor =
    if (BuildConfig.DEBUG && LOG_NETWORK_REQUESTS) {
        HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY)
    } else {
        HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.NONE)
    }

object PlexRequestSingleton {
    private var _url = PLEX_LOGIN_SERVICE_URL
    var url: String
        get() = _url
        set(value) {
            _url = value
            _observableUrl.postValue(value)
        }

    fun isUrlSet(): Boolean {
        return url.isNotEmpty() && url != PLEX_LOGIN_SERVICE_URL
    }

    private var _observableUrl = MutableLiveData<String>()
    val observableUrl: LiveData<String>
        get() = _observableUrl

    val connectionSet = mutableSetOf<Connection>()
    var libraryId = ""

    private var _authToken = ""
    var authToken: String
        set(value) {
            plexInterceptor.authToken = value
            _authToken = value
        }
        get() = _authToken

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

    fun makeGlideHeaders(): LazyHeaders {
        return LazyHeaders.Builder()
            .addHeader("X-Plex-Token", authToken)
            .build()
    }

    fun makeDownloadRequest(trackSource: String): DownloadManager.Request {
        return DownloadManager.Request(Uri.parse(toServerString(trackSource)))
            .addRequestHeader("X-Plex-Platform", "Android")
            .addRequestHeader("X-Plex-Provides", "player,timeline")
            .addRequestHeader("X-Plex-Client-Name", APP_NAME)
            .addRequestHeader(
                "X-Plex-Client-Identifier",
                "Unique client identifier lmao"
            ) // TODO- add a real uuid
            .addRequestHeader("X-Plex-Version", BuildConfig.VERSION_NAME)
            .addRequestHeader("X-Plex-Product", APP_NAME)
            .addRequestHeader("X-Plex-Platform-Version", Build.VERSION.RELEASE)
            .addRequestHeader("X-Plex-Device", Build.MODEL)
            .addRequestHeader("X-Plex-Device-Name", Build.MODEL)
            .addRequestHeader("X-Plex-Session-Identifier", sessionIdentifier)
            .addRequestHeader("X-Plex-Token", authToken)
    }

    @InternalCoroutinesApi
    @UseExperimental(ExperimentalCoroutinesApi::class)
    suspend fun chooseViableConnections(scope: CoroutineScope) {
        Log.i(APP_NAME, "Choosing viable connection")
        val validUriChannel = Channel<String>(capacity = 1)
        val resultsChannel = Channel<Pair<String, Boolean>>(capacity = connectionSet.size)
        connectionSet.sortedByDescending { it.local }.forEach { conn ->
            scope.launch {
                Log.i(APP_NAME, "Checking uri: ${conn.uri}")
                try {
                    PlexMediaApi.retrofitService.checkServer(conn.uri)
                    validUriChannel.send(conn.uri)
                    resultsChannel.send(conn.uri to true)
                    Log.i(APP_NAME, "Choosing uri: ${conn.uri}")
                    validUriChannel.close()
                } catch (e: Exception) {
                    resultsChannel.send(conn.uri to false)
                    Log.e(APP_NAME, "Connection failed: ${conn.uri}")
                }
            }
        }


        val result = resultsChannel.receive()
        Log.i(APP_NAME, "Connection result: ${result.second} for ${result.first}")

        val uri = validUriChannel.receive()
        Log.i(APP_NAME, "Uri is? $uri")
        validUriChannel.close()
        url = uri
    }

    fun clear() {
        _authToken = ""
        connectionSet.clear()
        _url = PLEX_LOGIN_SERVICE_URL
    }
}

private val plexInterceptor = PlexInterceptor()
private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .addInterceptor(plexInterceptor)
    .addInterceptor(loggingInterceptor)
    .build()

/**
 * TODO: centralized retrofit error handling to allow retries with other servers
 *  @see https://stackoverflow.com/questions/35029936/centralized-error-handling-retrofit-2
 */
private val retrofit = Retrofit.Builder()
    .addConverterFactory(SimpleXmlConverterFactory.create())
    .client(okHttpClient)
    .baseUrl(PLEX_LOGIN_SERVICE_URL) // this will be replaced by PlexInterceptor
    .build()

object PlexLoginApi {
    val retrofitService: PlexLoginService by lazy {
        retrofit.create(PlexLoginService::class.java)
    }
}

interface PlexLoginService {
    @POST("/users/sign_in.xml")
    suspend fun signIn(@Header("Authorization") authorization: String): User

    @GET("/pms/resources?includeHttps=1")
    suspend fun resources(): MediaContainer
}


interface PlexMediaService {
    /**
     * A basic check used to tell whether a server is online. Returns the lightest reponse AFAIK
     */
    @GET("{url}/identity")
    suspend fun checkServer(@Path("url", encoded = true) url: String): MediaContainer

    @GET("/library/sections/{libraryId}/all?type=$MEDIA_TYPE_ALBUM")
    suspend fun retrieveAllAlbums(@Path("libraryId") libraryId: String): MediaContainer

    @GET("/library/metadata/{albumId}/children")
    suspend fun retrieveTracksForAlbum(@Path("albumId") albumId: Int): MediaContainer

    @GET("/library/sections")
    suspend fun retrieveSections(): MediaContainer

    @GET("{url}")
    @Streaming
    suspend fun retrieveStreamByFilePath(
        @Path(
            value = "url",
            encoded = true
        ) url: String
    ): ResponseBody


    /** Sets a media item to "watched" in the server */
    @GET("/:/scrobble?identifier=com.plexapp.plugins.library")
    suspend fun watched(
        @Query("key") key: String,
        @Query("identifier") identifier: String = "com.plexapp.plugins.library"
    ): String

    /** Sets a media item to "unwatched" in the server */
    @GET("/:/unscrobble?identifier=com.plexapp.plugins.library")
    suspend fun unwatched(
        @Query("key") key: String,
        @Query("identifier") identifier: String = "com.plexapp.plugins.library"
    ): String

    /**
     * Updates the runtime of a media item with a corresponding [ratingKey] to [offset], the number
     * of milliseconds progress from the start of the media item
     */
    @GET("/:/timeline")
    fun progress(
        @Query("ratingKey") ratingKey: String,
        @Query("time") offset: String,
        @Query("key") key: String,
        @Query("duration") duration: Long,
        @Query("state") playState: String,
        @Query("hasMDE") hasMde: Int,
        @Query("identifier") identifier: String = "com.plexapp.plugins.library",
        @Query("playbackTime") playbackTime: Long = 0L,
        @Query("playQueueItemId") playQueueItemId: Long = 0L
    ): Call<Unit>

    /**
     * Starts a media session (like you would see in the "activity dashboard" in the Plex web app
     * for a media item identified by a server and ID combination. This session can be updated using
     * the [progress] function
     *
     * Note: Initial exploration indicates that this call is required in order for [progress]
     * updates to register in Plex, although I haven't confirmed it 100%
     */
    @POST("/playQueues")
    fun startMediaSession(
        /** [serverUri] is in the form: "server://<MACHINE_IDENTIFIER>/com.plexapp.plugins.library/library/metadata/<BOOK_ID> */
        @Query("uri") serverUri: String,
        @Query("type") mediaType: String = MediaType.AUDIO_STRING,
        @Query("repeat") shouldRepeat: Boolean = false,
        @Query("own") isOwnedByUser: Boolean = true,
        @Query("includeChapters") shouldIncludeChapters: Boolean = true
    ): Call<Unit>

    /** Loads all [MediaType.TRACK]s available in the server */
    @GET("/library/sections/{libraryId}/all?type=$MEDIA_TYPE_TRACK")
    suspend fun retrieveAllTracksInLibrary(@Path("libraryId") libraryId: String): MediaContainer
}

object PlexMediaApi {
    val retrofitService: PlexMediaService by lazy {
        retrofit.create(PlexMediaService::class.java)
    }
}

