package io.github.mattpvaughn.chronicle.data.sources

import android.content.Context
import com.squareup.moshi.Moshi
import io.github.mattpvaughn.chronicle.data.sources.demo.DemoMediaSource
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexLibrarySource
import io.github.mattpvaughn.chronicle.injection.components.AppComponent.Companion.USER_AGENT
import okhttp3.logging.HttpLoggingInterceptor
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** Class containing information needed to instantiate any [MediaSource] child */
@Singleton
class MediaSourceFactory @Inject constructor(
    private val applicationContext: Context,
    private val loggingInterceptor: HttpLoggingInterceptor,
    private val moshi: Moshi,
    @Named(USER_AGENT)
    private val userAgent: String
) {
    fun create(id: Long, type: String): MediaSource {
        return when (type) {
            DemoMediaSource.TAG -> {
                DemoMediaSource(id, applicationContext)
            }
            PlexLibrarySource.TAG -> {
                PlexLibrarySource(id, applicationContext, loggingInterceptor, moshi)
            }
            else -> TODO("Type not yet supported")
        }
    }
}