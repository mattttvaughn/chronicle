package io.github.mattpvaughn.chronicle.data.plex

import android.os.Build
import io.github.mattpvaughn.chronicle.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response


/**
 * Injects plex required headers and injects the server url
 */
class PlexInterceptor : Interceptor {
    var authToken: String = ""

    override fun intercept(chain: Interceptor.Chain): Response {
        val interceptedUrl =
            chain.request().url.toString().replace(PLEX_LOGIN_SERVICE_URL, PlexRequestSingleton.url)
        val requestBuilder = chain.request().newBuilder()
            .header("X-Plex-Platform", "Android")
            .header("X-Plex-Provides", "player")
            .header("X-Plex-Client-Identifier", APP_NAME) // TODO- add a real uuid
            .header("X-Plex-Version", BuildConfig.VERSION_NAME)
            .header("X-Plex-Product", APP_NAME)
            .header("X-Plex-Platform-Version", Build.VERSION.RELEASE)
            .header("X-Plex-Device", Build.MODEL)
            .header("X-Plex-Device-Name", Build.MODEL)
            .url(interceptedUrl)

        if (authToken.isNotEmpty()) {
            requestBuilder.header("X-Plex-Token", authToken)
                .header("X-Plex-Session-Identifier", PlexRequestSingleton.sessionIdentifier)
                .header("X-Plex-Client-Name", APP_NAME).build()
        }
        return chain.proceed(requestBuilder.build())
    }
}
