package io.github.mattpvaughn.chronicle.injection.modules

import android.app.Application
import android.app.DownloadManager
import android.app.Service
import android.content.Context
import android.content.Context.MODE_PRIVATE
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import io.github.mattpvaughn.chronicle.data.local.*
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME
import io.github.mattpvaughn.chronicle.data.plex.PlexPrefsRepo
import io.github.mattpvaughn.chronicle.data.plex.SharedPreferencesPlexPrefsRepo
import io.github.mattpvaughn.chronicle.features.settings.PrefsRepo
import io.github.mattpvaughn.chronicle.features.settings.SharedPreferencesPrefsRepo
import java.io.File
import javax.inject.Named
import javax.inject.Singleton

@Module
class AppModule(private val app: Application) {
    @Provides
    @Singleton
    @Named("app")
    internal fun provideAppContext(): Context = app

    @Provides
    @Singleton
    fun providePlexPrefsRepo(): PlexPrefsRepo =
        SharedPreferencesPlexPrefsRepo(app.getSharedPreferences(APP_NAME, MODE_PRIVATE))

    @Provides
    @Singleton
    fun providePrefsRepo(): PrefsRepo =
        SharedPreferencesPrefsRepo(app.getSharedPreferences(APP_NAME, MODE_PRIVATE))

    @Provides
    @Singleton
    fun provideTrackRepo(): ITrackRepository =
        TrackRepository(getTrackDatabase(app.applicationContext).trackDao, providePrefsRepo())

    @Provides
    @Singleton
    fun provideBookRepo(): IBookRepository =
        BookRepository(getBookDatabase(app.applicationContext).bookDao, providePrefsRepo())

    @Provides
    @Singleton
    internal fun externalDeviceDirs(): List<File> = ContextCompat.getExternalFilesDirs(app.applicationContext, null).toList().filter { it.exists() }

    @Provides
    @Singleton
    fun workManager(): WorkManager = WorkManager.getInstance(app)

    @Provides
    @Singleton
    fun downloadManager(): DownloadManager = app.getSystemService(Service.DOWNLOAD_SERVICE) as DownloadManager
}