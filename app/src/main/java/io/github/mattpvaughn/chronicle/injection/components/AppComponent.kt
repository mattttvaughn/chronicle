package io.github.mattpvaughn.chronicle.injection.components

import android.app.DownloadManager
import android.content.Context
import androidx.work.WorkManager
import dagger.Component
import io.github.mattpvaughn.chronicle.data.local.IBookRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository
import io.github.mattpvaughn.chronicle.data.plex.PlexPrefsRepo
import io.github.mattpvaughn.chronicle.features.settings.PrefsRepo
import io.github.mattpvaughn.chronicle.injection.modules.AppModule
import java.io.File
import javax.inject.Named
import javax.inject.Singleton

@Singleton
@Component(modules = [AppModule::class])
interface AppComponent {
    fun externalDeviceDirs(): List<File>
    @Named("app")
    fun applicationContext(): Context
    fun plexPrefs(): PlexPrefsRepo
    fun prefsRepo(): PrefsRepo
    fun trackRepo(): ITrackRepository
    fun bookRepo(): IBookRepository
    fun workManager(): WorkManager
    fun downloadManager(): DownloadManager
}