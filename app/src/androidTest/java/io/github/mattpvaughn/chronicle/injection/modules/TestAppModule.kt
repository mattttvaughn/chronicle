package io.github.mattpvaughn.chronicle.injection.modules

import android.app.Application
import android.app.DownloadManager
import android.app.Service
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import io.github.mattpvaughn.chronicle.data.local.*
import io.github.mattpvaughn.chronicle.data.plex.PlexPrefsRepo
import io.github.mattpvaughn.chronicle.features.settings.PrefsRepo
import io.mockk.mockk
import java.io.File
import javax.inject.Named
import javax.inject.Singleton

@Module
class TestAppModule(private val app: Application) {
    @Provides
    @Singleton
    @Named("app")
    internal fun provideAppContext(): Context = app

    @Provides
    @Singleton
    fun providePlexPrefsRepo(): PlexPrefsRepo = mockk()

    @Provides
    @Singleton
    fun providePrefsRepo(): PrefsRepo = mockk()

    @Provides
    @Singleton
    fun provideTrackRepo(): ITrackRepository = FakeTrackRepository()

    @Provides
    @Singleton
    fun provideBookRepo(): IBookRepository = FakeBookRepository()

    @Provides
    @Singleton
    internal fun externalDeviceDirs(): List<File> = ContextCompat.getExternalFilesDirs(app.applicationContext, null).toList().filter { it.exists() }

    @Provides
    @Singleton
    fun workManager(): WorkManager = mockk()

    @Provides
    @Singleton
    fun downloadManager(): DownloadManager = mockk()
}