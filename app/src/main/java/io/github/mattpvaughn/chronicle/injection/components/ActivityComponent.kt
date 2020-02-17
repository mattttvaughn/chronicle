package io.github.mattpvaughn.chronicle.injection.components

import android.content.Context
import dagger.Component
import io.github.mattpvaughn.chronicle.application.MainActivity
import io.github.mattpvaughn.chronicle.application.MainActivityViewModelFactory
import io.github.mattpvaughn.chronicle.data.plex.ICachedFileManager
import io.github.mattpvaughn.chronicle.features.currentlyplaying.CurrentlyPlayingViewModel
import io.github.mattpvaughn.chronicle.features.player.MediaServiceConnection
import io.github.mattpvaughn.chronicle.features.player.ProgressUpdater
import io.github.mattpvaughn.chronicle.injection.modules.ActivityModule
import io.github.mattpvaughn.chronicle.injection.modules.IActivityModule
import io.github.mattpvaughn.chronicle.injection.scopes.PerActivity
import javax.inject.Named

@PerActivity
@Component(dependencies = [AppComponent::class], modules = [ActivityModule::class])
interface ActivityComponent {
    @Named("activity")
    fun activityContext(): Context
    fun mediaServiceConnection(): MediaServiceConnection
    fun cachedFileManager(): ICachedFileManager
    fun mainActivityViewModelFactory(): MainActivityViewModelFactory
    fun currentlyPlayingViewModelFactory(): CurrentlyPlayingViewModel.Factory
    fun progressUpdater(): ProgressUpdater
    fun inject(activity: MainActivity)
}

