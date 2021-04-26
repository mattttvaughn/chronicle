package io.github.mattpvaughn.chronicle.injection.modules

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import com.android.billingclient.api.BillingClient
import com.google.android.exoplayer2.util.Util
import com.squareup.moshi.Moshi
import com.tonyodev.fetch2.Fetch
import com.tonyodev.fetch2.FetchConfiguration
import dagger.Module
import dagger.Provides
import io.github.mattpvaughn.chronicle.application.ChronicleApplication
import io.github.mattpvaughn.chronicle.application.ChronicleBillingManager
import io.github.mattpvaughn.chronicle.application.LOG_NETWORK_REQUESTS
import io.github.mattpvaughn.chronicle.data.APP_NAME
import io.github.mattpvaughn.chronicle.data.CachedFileManager
import io.github.mattpvaughn.chronicle.data.ICachedFileManager
import io.github.mattpvaughn.chronicle.data.local.*
import io.github.mattpvaughn.chronicle.features.currentlyplaying.CurrentlyPlaying
import io.github.mattpvaughn.chronicle.features.currentlyplaying.CurrentlyPlayingSingleton
import io.github.mattpvaughn.chronicle.injection.components.AppComponent.Companion.USER_AGENT
import kotlinx.coroutines.CoroutineExceptionHandler
import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber
import java.io.File
import javax.inject.Named
import javax.inject.Singleton

@Module
class AppModule(private val app: ChronicleApplication) {
    companion object {
        const val OKHTTP_CLIENT_MEDIA = "Media"
        const val OKHTTP_CLIENT_LOGIN = "Login"
    }

    @Provides
    @Singleton
    fun provideContext(): Context = app.applicationContext

    @Provides
    @Singleton
    fun provideApplication(): ChronicleApplication = app

    @Provides
    @Singleton
    fun provideSharedPrefs(): SharedPreferences = app.getSharedPreferences(APP_NAME, MODE_PRIVATE)

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
    fun provideChapterDao(): ChapterDao = getChapterDatabase(app.applicationContext).chapterDao

    @Provides
    @Singleton
    fun provideChapterRepo(chapterRepo: ChapterRepository): IChapterRepository = chapterRepo

    @Provides
    @Singleton
    fun provideInternalDeviceDirs(): File = app.applicationContext.filesDir

    @Provides
    @Singleton
    fun provideExternalDeviceDirs(): List<File> =
        ContextCompat.getExternalFilesDirs(app.applicationContext, null).toList()

    @Provides
    @Singleton
    fun workManager(): WorkManager = WorkManager.getInstance(app)

    @Provides
    @Singleton
    fun fetchConfig(appContext: Context): FetchConfiguration =
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
        if (LOG_NETWORK_REQUESTS) {
            HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY)
        } else {
            HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.NONE)
        }

    @Provides
    @Singleton
    fun moshi(): Moshi = Moshi.Builder().build()

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
    @Named(USER_AGENT)
    fun userAgent(context: Context): String = Util.getUserAgent(
        context,
        APP_NAME
    )

    @Provides
    @Singleton
    fun provideCachedFileManager(cacheManager: CachedFileManager): ICachedFileManager = cacheManager

    @Provides
    @Singleton
    fun provideCurrentlyPlaying(): CurrentlyPlaying = CurrentlyPlayingSingleton()

}