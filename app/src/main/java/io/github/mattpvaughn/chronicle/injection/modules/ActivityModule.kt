package io.github.mattpvaughn.chronicle.injection.modules

import android.content.ComponentName
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dagger.Module
import dagger.Provides
import io.github.mattpvaughn.chronicle.features.player.MediaPlayerService
import io.github.mattpvaughn.chronicle.features.player.MediaServiceConnection
import io.github.mattpvaughn.chronicle.features.player.ProgressUpdater
import io.github.mattpvaughn.chronicle.features.player.SimpleProgressUpdater
import io.github.mattpvaughn.chronicle.injection.scopes.ActivityScope
import io.github.mattpvaughn.chronicle.util.ServiceUtils
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber

@Module
class ActivityModule(private val activity: AppCompatActivity) {
    @Provides
    @ActivityScope
    fun activity(): AppCompatActivity = activity

    @Provides
    @ActivityScope
    fun coroutineScope(): CoroutineScope = activity.lifecycleScope

    @Provides
    @ActivityScope
    fun fragmentManager(): FragmentManager = activity.supportFragmentManager

    @Provides
    @ActivityScope
    fun provideProgressUpdater(progressUpdater: SimpleProgressUpdater): ProgressUpdater =
        progressUpdater

    @Provides
    @ActivityScope
    fun provideBroadcastManager(): LocalBroadcastManager =
        LocalBroadcastManager.getInstance(activity)

    @Provides
    @ActivityScope
    fun mediaServiceConnection(): MediaServiceConnection {
        val conn = MediaServiceConnection(
            activity.applicationContext,
            ComponentName(activity.applicationContext, MediaPlayerService::class.java)
        )
        val doesServiceExist = ServiceUtils.isServiceRunning(
            activity.applicationContext,
            MediaPlayerService::class.java
        )
        Timber.i("Connecting to existing service? $doesServiceExist")
        if (doesServiceExist) {
            conn.connect()
        }
        return conn
    }


}


