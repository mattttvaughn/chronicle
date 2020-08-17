package io.github.mattpvaughn.chronicle.data.sources.plex

import android.os.Build
import io.github.mattpvaughn.chronicle.BuildConfig
import io.github.mattpvaughn.chronicle.data.APP_NAME
import okhttp3.Interceptor
import okhttp3.Response


/**
 * Injects plex required headers
 *
 * If accessing a media server instead of just plex.tv, inject the server url
 */
class PlexInterceptor(
    private val plexPrefsRepo: PlexPrefsRepo,
    private val plexLibrarySource: PlexLibrarySource,
    private val isLoginService: Boolean
) : Interceptor {

    companion object {
        const val PLATFORM = "Android"
        const val PRODUCT = APP_NAME
        const val DEVICE = "$APP_NAME $PLATFORM"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val interceptedUrl =
            chain.request().url.toString().replace(PLACEHOLDER_URL, plexLibrarySource.url)

        val requestBuilder = chain.request().newBuilder()
            .header("Accept", "application/json")
            .header("X-Plex-Platform", PLATFORM)
            .header("X-Plex-Provides", "player")
            .header("X-Plex-Client-Identifier", plexPrefsRepo.uuid)
            .header("X-Plex-Version", BuildConfig.VERSION_NAME)
            .header("X-Plex-Product", PRODUCT)
            .header("X-Plex-Platform-Version", Build.VERSION.RELEASE)
            .header("X-Plex-Session-Identifier", plexLibrarySource.sessionIdentifier)
            .header(
                "X-Plex-Client-Name",
                APP_NAME
            )
            .header("X-Plex-Device", DEVICE)
            .header("X-Plex-Device-Name", Build.MODEL)
            .url(interceptedUrl)

        val authToken = if (isLoginService) {
            plexPrefsRepo.user?.authToken ?: plexPrefsRepo.accountAuthToken
        } else {
            plexPrefsRepo.server?.accessToken ?: plexPrefsRepo.accountAuthToken
        }

        if (authToken.isNotEmpty()) {
            requestBuilder.header("X-Plex-Token", authToken)
        }

        return chain.proceed(requestBuilder.build())
    }

}
