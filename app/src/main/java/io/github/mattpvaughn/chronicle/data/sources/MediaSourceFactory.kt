package io.github.mattpvaughn.chronicle.data.sources

import com.squareup.moshi.Moshi
import io.github.mattpvaughn.chronicle.application.ChronicleApplication
import io.github.mattpvaughn.chronicle.data.sources.demo.DemoMediaSource
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexLibrarySource
import okhttp3.logging.HttpLoggingInterceptor
import javax.inject.Inject
import javax.inject.Singleton

/** Class containing information needed to instantiate any [MediaSource] child */
@Singleton
class MediaSourceFactory @Inject constructor(
    private val application: ChronicleApplication,
    private val loggingInterceptor: HttpLoggingInterceptor,
    private val moshi: Moshi
) {
    fun create(type: String, id: Long): MediaSource {
        return when (type) {
            DemoMediaSource.TAG -> {
                DemoMediaSource(id, application)
            }
            PlexLibrarySource.TAG -> {
                PlexLibrarySource(id, application, loggingInterceptor, moshi)
            }
            else -> TODO("Type not yet supported")
        }
    }
}