package io.github.mattpvaughn.chronicle.injection.components

import android.app.DownloadManager
import android.content.Context
import android.content.SharedPreferences
import androidx.work.WorkManager
import com.google.android.gms.cast.framework.CastContext
import com.squareup.moshi.Moshi
import dagger.Component
import io.github.mattpvaughn.chronicle.application.ChronicleApplication
import io.github.mattpvaughn.chronicle.application.ChronicleBillingManager
import io.github.mattpvaughn.chronicle.data.local.*
import io.github.mattpvaughn.chronicle.data.sources.plex.*
import io.github.mattpvaughn.chronicle.features.login.ChooseLibraryFragment
import io.github.mattpvaughn.chronicle.features.login.ChooseServerFragment
import io.github.mattpvaughn.chronicle.features.login.ChooseUserFragment
import io.github.mattpvaughn.chronicle.features.login.LoginFragment
import io.github.mattpvaughn.chronicle.injection.modules.AppModule
import kotlinx.coroutines.CoroutineExceptionHandler
import java.io.File
import javax.inject.Singleton

@Singleton
@Component(modules = [AppModule::class])
interface AppComponent {
    fun applicationContext(): Context
    fun internalFilesDir(): File
    fun externalDeviceDirs(): List<File>
    fun sharedPrefs(): SharedPreferences
    fun trackDao(): TrackDao
    fun bookDao(): BookDao
    fun moshi(): Moshi
    fun plexLoginRepo(): IPlexLoginRepo
    fun plexPrefs(): PlexPrefsRepo
    fun prefsRepo(): PrefsRepo
    fun trackRepo(): ITrackRepository
    fun bookRepo(): IBookRepository
    fun workManager(): WorkManager
    fun downloadManager(): DownloadManager
    fun castContext(): CastContext
    fun plexConnectionChooser(): PlexConnectionChooser
    fun unhandledExceptionHandler(): CoroutineExceptionHandler
    fun plexConfig(): PlexConfig
    fun plexLoginService(): PlexLoginService
    fun plexMediaService(): PlexMediaService

    //    fun plexMediaSource(): PlexMediaSource
    fun billingManager(): ChronicleBillingManager

    // Inject
    fun inject(chronicleApplication: ChronicleApplication)
    fun inject(loginFragment: LoginFragment)
    fun inject(chooseLibraryFragment: ChooseLibraryFragment)
    fun inject(chooseUserFragment: ChooseUserFragment)
    fun inject(chooseServerActivity: ChooseServerFragment)
}