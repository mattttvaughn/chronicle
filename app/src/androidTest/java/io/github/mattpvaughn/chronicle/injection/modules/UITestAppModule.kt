package io.github.mattpvaughn.chronicle.injection.modules

import android.content.ComponentName
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.net.Uri
import android.support.v4.media.session.MediaControllerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.work.WorkManager
import com.facebook.imagepipeline.backends.okhttp3.OkHttpImagePipelineConfigFactory
import com.facebook.imagepipeline.cache.DefaultCacheKeyFactory
import com.facebook.imagepipeline.request.ImageRequest
import com.squareup.moshi.Moshi
import com.tonyodev.fetch2.Fetch
import com.tonyodev.fetch2.FetchConfiguration
import dagger.Module
import dagger.Provides
import io.github.mattpvaughn.chronicle.BuildConfig
import io.github.mattpvaughn.chronicle.application.LOG_NETWORK_REQUESTS
import io.github.mattpvaughn.chronicle.data.local.*
import io.github.mattpvaughn.chronicle.data.sources.plex.*
import io.github.mattpvaughn.chronicle.features.currentlyplaying.CurrentlyPlaying
import io.github.mattpvaughn.chronicle.features.currentlyplaying.CurrentlyPlayingSingleton
import io.github.mattpvaughn.chronicle.features.player.MediaPlayerService
import io.github.mattpvaughn.chronicle.features.player.MediaServiceConnection
import io.github.mattpvaughn.chronicle.views.UrlQueryCacheKey
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.CoroutineExceptionHandler
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
class UITestAppModule(private val context: Context) {
    companion object {
        const val OKHTTP_CLIENT_MEDIA = "Media_Test"
        const val OKHTTP_CLIENT_LOGIN = "Login_Test"
    }

    @Provides
    @Singleton
    fun provideContext() = context

    @Provides
    @Singleton
    fun provideSharedPrefs(): SharedPreferences = context.getSharedPreferences("test", MODE_PRIVATE)

    @Provides
    @Singleton
    fun providePlexPrefsRepo(prefs: SharedPreferencesPlexPrefsRepo): PlexPrefsRepo = spyk(prefs)

    @Provides
    @Singleton
    fun providePrefsRepo(prefs: SharedPreferencesPrefsRepo): PrefsRepo = spyk(prefs)

    @Provides
    @Singleton
    fun provideTrackDao() = getTrackDatabase(context).trackDao

    @Provides
    @Singleton
    fun provideTrackRepo(trackRepository: TrackRepository): ITrackRepository = spyk(trackRepository)

    @Provides
    @Singleton
    fun provideBookDao() = getBookDatabase(context).bookDao

    @Provides
    @Singleton
    fun provideBookRepo(bookRepository: BookRepository): IBookRepository = spyk(bookRepository)

    @Provides
    @Singleton
    fun provideCollectionsDao(): CollectionsDao = getCollectionsDatabase(context).collectionsDao

    @Provides
    @Singleton
    fun provideInternalDeviceDirs(): File = context.filesDir

    @Provides
    @Singleton
    fun provideExternalDeviceDirs(): List<File> =
        ContextCompat.getExternalFilesDirs(
            context,
            null,
        ).toList()

    @Provides
    @Singleton
    fun provideLoginRepo(plexLoginRepo: PlexLoginRepo): IPlexLoginRepo = spyk(plexLoginRepo)

    @Provides
    @Singleton
    fun workManager(): WorkManager = WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun fetchConfig(
        appContext: Context,
        @Named(OKHTTP_CLIENT_MEDIA)
        okHttpClient: OkHttpClient,
    ): FetchConfiguration =
        FetchConfiguration.Builder(appContext)
            .setDownloadConcurrentLimit(3)
            .createDownloadFileOnEnqueue(false)
            .enableAutoStart(false)
            .setAutoRetryMaxAttempts(1)
            // TODO: this was broken when I set up Fetch, maybe figure it out at some point?
//            .setHttpDownloader(OkHttpDownloader(okHttpClient))
            .enableLogging(true)
            .build()

    @Provides
    @Singleton
    fun fetch(fetchConfig: FetchConfiguration): Fetch = Fetch.Impl.getInstance(fetchConfig)

    @Provides
    @Singleton
    fun loggingInterceptor() =
        if (BuildConfig.DEBUG && LOG_NETWORK_REQUESTS) {
            HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY)
        } else {
            HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.NONE)
        }

    @Provides
    @Singleton
    @Named(OKHTTP_CLIENT_MEDIA)
    fun mediaOkHttpClient(
        plexConfig: PlexConfig,
        loggingInterceptor: HttpLoggingInterceptor,
    ): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .protocols(listOf(Protocol.HTTP_1_1, Protocol.QUIC))
            .addInterceptor(plexConfig.plexMediaInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()

    @Provides
    @Singleton
    @Named(OKHTTP_CLIENT_LOGIN)
    fun loginOkHttpClient(
        plexConfig: PlexConfig,
        loggingInterceptor: HttpLoggingInterceptor,
    ): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(plexConfig.plexLoginInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()

    @Provides
    @Named(OKHTTP_CLIENT_MEDIA)
    @Singleton
    fun mediaRetrofit(
        @Named(OKHTTP_CLIENT_MEDIA) okHttpClient: OkHttpClient,
    ): Retrofit =
        Retrofit.Builder()
            .addConverterFactory(MoshiConverterFactory.create())
            .client(okHttpClient)
            .baseUrl(PLACEHOLDER_URL) // this will be replaced by PlexInterceptor as needed
            .build()

    @Provides
    @Named(OKHTTP_CLIENT_LOGIN)
    @Singleton
    fun loginRetrofit(
        @Named(OKHTTP_CLIENT_LOGIN) okHttpClient: OkHttpClient,
    ): Retrofit =
        Retrofit.Builder()
            .addConverterFactory(MoshiConverterFactory.create())
            .client(okHttpClient)
            .baseUrl(PLACEHOLDER_URL) // this will be replaced by PlexInterceptor as needed
            .build()

    @Provides
    @Singleton
    fun provideMediaServiceConnection(): MediaServiceConnection {
        return MediaServiceConnection(
            context,
            ComponentName(context, MediaPlayerService::class.java),
        )
    }

    @Provides
    @Singleton
    fun provideMediaController(mediaServiceConnection: MediaServiceConnection): MediaControllerCompat? =
        mediaServiceConnection.mediaController

    @Provides
    @Singleton
    fun moshi(): Moshi = Moshi.Builder().build()

    @Provides
    @Singleton
    fun plexMediaService(): PlexMediaService = mockk(relaxed = false)

    @Provides
    @Singleton
    fun plexLoginService(): PlexLoginService = mockk(relaxed = false)

    @Provides
    @Singleton
    fun exceptionHandler(): CoroutineExceptionHandler =
        CoroutineExceptionHandler { _, e ->
            Timber.e("Caught unhandled exception! $e")
        }

    @Provides
    @Singleton
    fun provideCurrentlyPlaying(): CurrentlyPlaying = CurrentlyPlayingSingleton()

    @Provides
    @Singleton
    fun provideCachedFileManager(cacheManager: CachedFileManager): ICachedFileManager = cacheManager

    @Provides
    @Singleton
    fun frescoConfig(
        @Named(OKHTTP_CLIENT_MEDIA)
        okHttpClient: OkHttpClient,
    ) = OkHttpImagePipelineConfigFactory
        .newBuilder(context, okHttpClient)
        .setCacheKeyFactory(
            object : DefaultCacheKeyFactory() {
                override fun getEncodedCacheKey(
                    request: ImageRequest?,
                    sourceUri: Uri?,
                    callerContext: Any?,
                ) = UrlQueryCacheKey(sourceUri)

                override fun getEncodedCacheKey(
                    request: ImageRequest?,
                    callerContext: Any?,
                ) = UrlQueryCacheKey(request?.sourceUri)

                override fun getBitmapCacheKey(
                    request: ImageRequest?,
                    callerContext: Any?,
                ) = UrlQueryCacheKey(request?.sourceUri)

                override fun getPostprocessedBitmapCacheKey(
                    request: ImageRequest?,
                    callerContext: Any?,
                ) = UrlQueryCacheKey(request?.sourceUri)

                override fun getCacheKeySourceUri(sourceUri: Uri?): Uri {
                    return sourceUri?.query?.toUri() ?: "".toUri()
                }
            },
        )
        .build()
}
