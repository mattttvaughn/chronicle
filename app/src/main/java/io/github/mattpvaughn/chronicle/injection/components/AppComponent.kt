package io.github.mattpvaughn.chronicle.injection.components

import android.app.DownloadManager
import android.content.Context
import android.content.SharedPreferences
import androidx.work.WorkManager
import com.squareup.moshi.Moshi
import dagger.Component
import io.github.mattpvaughn.chronicle.application.ChronicleApplication
import io.github.mattpvaughn.chronicle.application.ChronicleBillingManager
import io.github.mattpvaughn.chronicle.data.local.*
import io.github.mattpvaughn.chronicle.data.sources.MediaSourceFactory
import io.github.mattpvaughn.chronicle.data.sources.SourceManager
import io.github.mattpvaughn.chronicle.injection.modules.AppModule
import kotlinx.coroutines.CoroutineExceptionHandler
import java.io.File
import javax.inject.Singleton

@Singleton
@Component(modules = [AppModule::class])
interface AppComponent {
    fun applicationContext(): Context
    fun application(): ChronicleApplication
    fun internalFilesDir(): File
    fun externalDeviceDirs(): List<File>
    fun sharedPrefs(): SharedPreferences
    fun trackDao(): TrackDao
    fun bookDao(): BookDao
    fun moshi(): Moshi
    fun prefsRepo(): PrefsRepo
    fun trackRepo(): ITrackRepository
    fun bookRepo(): IBookRepository
    fun workManager(): WorkManager
    fun downloadManager(): DownloadManager
    fun unhandledExceptionHandler(): CoroutineExceptionHandler
    fun mediaSourceFactory(): MediaSourceFactory
    fun sourceManager(): SourceManager
    fun billingManager(): ChronicleBillingManager

    fun inject(chronicleApplication: ChronicleApplication)
}