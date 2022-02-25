package io.github.mattpvaughn.chronicle.data.sources.plex

import android.os.Build
import io.github.mattpvaughn.chronicle.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber


/**
 * Injects plex required headers
 *
 * If accessing a media server instead of just plex.tv, inject the server url
 */
class PlexInterceptor(
    private val plexPrefsRepo: PlexPrefsRepo,
    private val plexConfig: PlexConfig,
    private val isLoginService: Boolean
) : Interceptor {

    init {
        if (isLoginService) {
            Timber.i("Inited login intercepter")
        } else {
            Timber.i("Inited media intercepter")
        }
    }

    companion object {
        const val PLATFORM = "Android"
        const val PRODUCT = APP_NAME
        const val DEVICE = "$APP_NAME $PLATFORM"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val interceptedUrl = chain.request().url.toString().replace(PLACEHOLDER_URL, plexConfig.url)

        val requestBuilder = chain.request().newBuilder()
            .header("Accept", "application/json")
            .header("X-Plex-Platform", PLATFORM)
            .header("X-Plex-Provides", "player")
            .header("X-Plex-Client-Identifier", plexPrefsRepo.uuid)
            .header("X-Plex-Version", BuildConfig.VERSION_NAME)
            .header("X-Plex-Product", PRODUCT)
            .header("X-Plex-Platform-Version", Build.VERSION.RELEASE)
            .header("X-Plex-Session-Identifier", plexConfig.sessionIdentifier)
            .header("X-Plex-Client-Name", APP_NAME)
            .header("X-Plex-Device", DEVICE)
            .header("X-Plex-Device-Name", Build.MODEL)
            .url(interceptedUrl)

        val userToken = plexPrefsRepo.user?.authToken
        val serverToken = plexPrefsRepo.server?.accessToken
        val accountToken = plexPrefsRepo.accountAuthToken

        val serviceToken = if (isLoginService) userToken else serverToken
        val authToken = if (serviceToken.isNullOrEmpty()) accountToken else serviceToken

        if (authToken.isNotEmpty()) {
            requestBuilder.header("X-Plex-Token", authToken)
        }

        return chain.proceed(requestBuilder.build())
    }

}
