package io.github.mattpvaughn.chronicle.data.plex

import android.app.DownloadManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.bumptech.glide.load.model.LazyHeaders
import io.github.mattpvaughn.chronicle.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class PlexConfig @Inject constructor() {
    private var _url = PLACEHOLDER_URL
    var url: String
        get() = _url
        set(value) {
            _url = value
            Log.i(APP_NAME, "Url set to: $value")
            _isConnected.postValue(value != PLACEHOLDER_URL)
        }

    private var _isConnected = MutableLiveData<Boolean>()
    val isConnected: LiveData<Boolean>
        get() = _isConnected

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
        Log.i(APP_NAME, "Preparing download request for: ${Uri.parse(toServerString(trackSource))}")
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

    fun clear() {
        authToken = ""
        url = PLACEHOLDER_URL
        _isConnected.postValue(false)
    }

    val plexInterceptor: PlexInterceptor by lazy {
        PlexInterceptor(this)
    }
}