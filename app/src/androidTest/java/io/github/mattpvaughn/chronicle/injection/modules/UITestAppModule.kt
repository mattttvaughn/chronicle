package io.github.mattpvaughn.chronicle.injection.modules

import android.app.DownloadManager
import android.content.ComponentName
import android.content.Context
import android.content.Context.DOWNLOAD_SERVICE
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.support.v4.media.session.MediaControllerCompat
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import io.github.mattpvaughn.chronicle.BuildConfig
import io.github.mattpvaughn.chronicle.application.LOG_NETWORK_REQUESTS
import io.github.mattpvaughn.chronicle.data.local.*
import io.github.mattpvaughn.chronicle.data.sources.plex.*
import io.github.mattpvaughn.chronicle.features.player.MediaPlayerService
import io.github.mattpvaughn.chronicle.features.player.MediaServiceConnection
import io.mockk.mockk
import io.mockk.spyk
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import javax.inject.Singleton

@Module
class UITestAppModule(private val context: Context) {
    @Provides
    @Singleton
    fun provideContext() = context

    @Provides
    @Singleton
    fun provideSharedPrefs(): SharedPreferences = context.getSharedPreferences("test", MODE_PRIVATE)

    @Provides
    @Singleton
    fun provideTrackDao() = getTrackDatabase(context).trackDao

    @Provides
    @Singleton
    fun provideBookDao() = getBookDatabase(context).bookDao

    @Provides
    @Singleton
    fun providePlexPrefsRepo(prefs: SharedPreferencesPlexPrefsRepo): PlexPrefsRepo = spyk(prefs)

    @Provides
    @Singleton
    fun providePrefsRepo(prefs: SharedPreferencesPrefsRepo): PrefsRepo = spyk(prefs)

    @Provides
    @Singleton
    fun provideTrackRepo(trackRepository: TrackRepository): ITrackRepository = spyk(trackRepository)

    @Provides
    @Singleton
    fun provideBookRepo(bookRepository: BookRepository): IBookRepository = spyk(bookRepository)

    @Provides
    @Singleton
    fun provideLoginRepo(plexLoginRepo: PlexLoginRepo): IPlexLoginRepo = spyk(plexLoginRepo)

    @Provides
    @Singleton
    fun externalDeviceDirs(): List<File> =
        ContextCompat.getExternalFilesDirs(context, null).toList()

    @Provides
    @Singleton
    fun provideInternalDeviceDirs(): File = context.filesDir

    @Provides
    @Singleton
    fun provideMediaServiceConnection(): MediaServiceConnection {
        return MediaServiceConnection(
            context,
            ComponentName(context, MediaPlayerService::class.java)
        )
    }

    @Provides
    @Singleton
    fun provideMediaController(mediaServiceConnection: MediaServiceConnection): MediaControllerCompat? =
        mediaServiceConnection.mediaController

    @Provides
    @Singleton
    fun plexConnectionChooser(
        plexLibrarySource: PlexLibrarySource,
        plexMediaService: PlexMediaService
    ) =
        spyk(PlexConnectionChooser(plexLibrarySource, plexMediaService))

    @Provides
    @Singleton
    fun workManager(): WorkManager = WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun downloadManager(): DownloadManager =
        context.getSystemService(DOWNLOAD_SERVICE) as DownloadManager

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
    fun plexMediaService(): PlexMediaService = mockk(relaxed = false)

    @Provides
    @Singleton
    fun plexLoginService(): PlexLoginService = mockk(relaxed = false)
}