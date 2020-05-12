package io.github.mattpvaughn.chronicle.injection.components

import android.app.DownloadManager
import android.content.Context
import androidx.work.WorkManager
import dagger.Component
import io.github.mattpvaughn.chronicle.application.ChronicleApplication
import io.github.mattpvaughn.chronicle.application.ChronicleBillingManager
import io.github.mattpvaughn.chronicle.data.local.*
import io.github.mattpvaughn.chronicle.data.plex.*
import io.github.mattpvaughn.chronicle.features.bookdetails.AudiobookDetailsFragment
import io.github.mattpvaughn.chronicle.features.home.HomeFragment
import io.github.mattpvaughn.chronicle.features.login.ChooseLibraryFragment
import io.github.mattpvaughn.chronicle.features.login.ChooseServerFragment
import io.github.mattpvaughn.chronicle.features.login.LoginFragment
import io.github.mattpvaughn.chronicle.features.player.MediaServiceConnection
import io.github.mattpvaughn.chronicle.features.settings.SettingsFragment
import io.github.mattpvaughn.chronicle.injection.modules.AppModule
import retrofit2.Retrofit
import java.io.File
import javax.inject.Singleton

@Singleton
@Component(modules = [AppModule::class])
interface AppComponent {
    fun applicationContext(): Context
    fun internalFilesDir(): File
    fun externalDeviceDirs(): List<File>
    fun trackDao(): TrackDao
    fun bookDao(): BookDao
    fun plexLoginRepo(): IPlexLoginRepo
    fun plexPrefs(): PlexPrefsRepo
    fun prefsRepo(): PrefsRepo
    fun trackRepo(): ITrackRepository
    fun bookRepo(): IBookRepository
    fun workManager(): WorkManager
    fun downloadManager(): DownloadManager
    fun mediaServiceConnection(): MediaServiceConnection
    fun retrofit(): Retrofit
    fun plexConfig(): PlexConfig
    fun plexLoginService(): PlexLoginService
    fun plexMediaService(): PlexMediaService
    fun billingManager(): ChronicleBillingManager

    // Inject
    fun inject(chronicleApplication: ChronicleApplication)
    fun inject(audiobookDetailsFragment: AudiobookDetailsFragment)
    fun inject(loginFragment: LoginFragment)
    fun inject(homeFragment: HomeFragment)
    fun inject(chooseLibraryFragment: ChooseLibraryFragment)
    fun inject(chooseServerActivity: ChooseServerFragment)
    fun inject(settingsFragment: SettingsFragment)
}