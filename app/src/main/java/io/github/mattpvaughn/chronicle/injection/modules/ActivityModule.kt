package io.github.mattpvaughn.chronicle.injection.modules

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dagger.Module
import dagger.Provides
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.data.plex.CachedFileManager
import io.github.mattpvaughn.chronicle.data.plex.ICachedFileManager
import io.github.mattpvaughn.chronicle.features.player.MediaPlayerService
import io.github.mattpvaughn.chronicle.features.player.MediaServiceConnection
import io.github.mattpvaughn.chronicle.features.player.ProgressUpdater
import io.github.mattpvaughn.chronicle.features.player.SimpleProgressUpdater
import io.github.mattpvaughn.chronicle.injection.scopes.PerActivity
import kotlinx.coroutines.CoroutineScope
import javax.inject.Named

interface IActivityModule {
    open fun provideActivityContext(): Context
    open fun provideMediaServiceConnection(): MediaServiceConnection
    open fun coroutineScope(): CoroutineScope
    open fun provideCachedFileManager(): ICachedFileManager
    open fun provideProgressUpdater(): ProgressUpdater
}

@Module
open class ActivityModule(private val activity: AppCompatActivity) : IActivityModule {

    @Provides
    @PerActivity
    @Named("activity")
    override fun provideActivityContext(): Context = activity

    @Provides
    @PerActivity
    override fun provideMediaServiceConnection(): MediaServiceConnection =
        MediaServiceConnection.getInstance(
            provideActivityContext() as Activity,
            ComponentName(provideActivityContext(), MediaPlayerService::class.java)
        )

    @Provides
    @PerActivity
    override fun coroutineScope(): CoroutineScope {
        return activity.lifecycleScope
    }

    @Provides
    @PerActivity
    override fun provideCachedFileManager(): ICachedFileManager {
        return CachedFileManager(
            Injector.get().downloadManager(),
            Injector.get().prefsRepo(),
            coroutineScope(),
            Injector.get().trackRepo(),
            Injector.get().bookRepo(),
            Injector.get().externalDeviceDirs()
        )
    }

    @Provides
    @PerActivity
    override fun provideProgressUpdater(): ProgressUpdater {
        return SimpleProgressUpdater(
            coroutineScope(),
            Injector.get().trackRepo(),
            Injector.get().bookRepo(),
            Injector.get().workManager(),
            provideMediaServiceConnection(),
            provideMediaServiceConnection().mediaController,
            Injector.get().prefsRepo()
        )
    }
}


