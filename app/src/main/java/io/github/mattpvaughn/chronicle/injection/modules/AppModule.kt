package io.github.mattpvaughn.chronicle.injection.modules

import android.app.Application
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import com.android.billingclient.api.BillingClient
import com.squareup.moshi.Moshi
import com.tonyodev.fetch2.Fetch
import com.tonyodev.fetch2.FetchConfiguration
import dagger.Module
import dagger.Provides
import io.github.mattpvaughn.chronicle.application.ChronicleBillingManager
import io.github.mattpvaughn.chronicle.application.LOG_NETWORK_REQUESTS
import io.github.mattpvaughn.chronicle.data.local.*
import io.github.mattpvaughn.chronicle.data.sources.plex.*
import io.github.mattpvaughn.chronicle.features.currentlyplaying.CurrentlyPlaying
import io.github.mattpvaughn.chronicle.features.currentlyplaying.CurrentlyPlayingSingleton
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
class AppModule(private val app: Application) {
    companion object {
        const val OKHTTP_CLIENT_MEDIA = "Media"
        const val OKHTTP_CLIENT_LOGIN = "Login"
    }

    @Provides
    @Singleton
    fun provideContext(): Context = app.applicationContext

    @Provides
    @Singleton
    fun provideSharedPrefs(): SharedPreferences = app.getSharedPreferences(APP_NAME, MODE_PRIVATE)

    @Provides
    @Singleton
    fun providePlexPrefsRepo(prefsImpl: SharedPreferencesPlexPrefsRepo): PlexPrefsRepo = prefsImpl

    @Provides
    @Singleton
    fun providePrefsRepo(prefsImpl: SharedPreferencesPrefsRepo): PrefsRepo = prefsImpl

    @Provides
    @Singleton
    fun provideTrackDao(): TrackDao = getTrackDatabase(app.applicationContext).trackDao

    @Provides
    @Singleton
    fun provideTrackRepo(trackRepository: TrackRepository): ITrackRepository = trackRepository

    @Provides
    @Singleton
    fun provideBookDao(): BookDao = getBookDatabase(app.applicationContext).bookDao

    @Provides
    @Singleton
    fun provideBookRepo(bookRepository: BookRepository): IBookRepository = bookRepository

    @Provides
    @Singleton
    fun provideInternalDeviceDirs(): File = app.applicationContext.filesDir

    @Provides
    @Singleton
    fun provideExternalDeviceDirs(): List<File> =
        ContextCompat.getExternalFilesDirs(app.applicationContext, null).toList()

    @Provides
    @Singleton
    fun loginRepo(plexLoginRepo: PlexLoginRepo): IPlexLoginRepo = plexLoginRepo

    @Provides
    @Singleton
    fun workManager(): WorkManager = WorkManager.getInstance(app)

    @Provides
    @Singleton
    fun fetchConfig(
        appContext: Context,
        @Named(OKHTTP_CLIENT_MEDIA) okHttpClient: OkHttpClient
    ): FetchConfiguration =
        FetchConfiguration.Builder(appContext)
            .setDownloadConcurrentLimit(3)
            .createDownloadFileOnEnqueue(false)
            .enableAutoStart(false)
            // TODO: this is broken, maybe use at some point
//            .setHttpDownloader(OkHttpDownloader(okHttpClient))
            .enableLogging(true)
            .build()

    @Provides
    @Singleton
    fun fetch(fetchConfig: FetchConfiguration): Fetch = Fetch.Impl.getInstance(fetchConfig)

    @Provides
    @Singleton
    fun loggingInterceptor() =
        if (LOG_NETWORK_REQUESTS) {
            HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY)
        } else {
            HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.NONE)
        }

    @Provides
    @Singleton
    @Named(OKHTTP_CLIENT_MEDIA)
    fun mediaOkHttpClient(
        plexConfig: PlexConfig,
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient = OkHttpClient.Builder()
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
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(plexConfig.plexLoginInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()

    @Provides
    @Named(OKHTTP_CLIENT_MEDIA)
    @Singleton
    fun mediaRetrofit(@Named(OKHTTP_CLIENT_MEDIA) okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .addConverterFactory(MoshiConverterFactory.create())
            .client(okHttpClient)
            .baseUrl(PLACEHOLDER_URL) // this will be replaced by PlexInterceptor as needed
            .build()

    @Provides
    @Named(OKHTTP_CLIENT_LOGIN)
    @Singleton
    fun loginRetrofit(@Named(OKHTTP_CLIENT_LOGIN) okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .addConverterFactory(MoshiConverterFactory.create())
            .client(okHttpClient)
            .baseUrl(PLACEHOLDER_URL) // this will be replaced by PlexInterceptor as needed
            .build()

    @Provides
    @Singleton
    fun moshi(): Moshi = Moshi.Builder().build()

    @Provides
    @Singleton
    fun plexMediaService(@Named(OKHTTP_CLIENT_MEDIA) mediaRetrofit: Retrofit): PlexMediaService =
        mediaRetrofit.create(PlexMediaService::class.java)

    @Provides
    @Singleton
    fun plexLoginService(@Named(OKHTTP_CLIENT_LOGIN) loginRetrofit: Retrofit): PlexLoginService =
        loginRetrofit.create(PlexLoginService::class.java)

    @Provides
    @Singleton
    fun billingClient(billingManager: ChronicleBillingManager): BillingClient {
        return BillingClient.newBuilder(app.applicationContext)
            .enablePendingPurchases()
            .setListener(billingManager).build()
    }

    @Provides
    @Singleton
    fun exceptionHandler(): CoroutineExceptionHandler = CoroutineExceptionHandler { _, e ->
        Timber.e("Caught unhandled exception! $e")
    }

    @Provides
    @Singleton
    fun provideCachedFileManager(cacheManager: CachedFileManager): ICachedFileManager = cacheManager

    @Provides
    @Singleton
    fun provideCurrentlyPlaying(): CurrentlyPlaying = CurrentlyPlayingSingleton()

}