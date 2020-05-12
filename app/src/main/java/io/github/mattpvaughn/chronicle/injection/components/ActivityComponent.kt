package io.github.mattpvaughn.chronicle.injection.components

import android.support.v4.media.session.MediaControllerCompat
import dagger.Component
import io.github.mattpvaughn.chronicle.application.MainActivity
import io.github.mattpvaughn.chronicle.application.MainActivityViewModelFactory
import io.github.mattpvaughn.chronicle.data.plex.ICachedFileManager
import io.github.mattpvaughn.chronicle.features.currentlyplaying.CurrentlyPlayingFragment
import io.github.mattpvaughn.chronicle.features.currentlyplaying.CurrentlyPlayingViewModel
import io.github.mattpvaughn.chronicle.features.library.LibraryFragment
import io.github.mattpvaughn.chronicle.features.login.OnboardingActivity
import io.github.mattpvaughn.chronicle.features.player.ProgressUpdater
import io.github.mattpvaughn.chronicle.injection.modules.ActivityModule
import io.github.mattpvaughn.chronicle.injection.scopes.ActivityScope
import io.github.mattpvaughn.chronicle.navigation.Navigator

@ActivityScope
@Component(dependencies = [AppComponent::class], modules = [ActivityModule::class])
interface ActivityComponent {
    fun cachedFileManager(): ICachedFileManager
    fun mainActivityViewModelFactory(): MainActivityViewModelFactory
    fun navigator(): Navigator
    fun progressUpdater(): ProgressUpdater
    fun currentPlayingViewModel(): CurrentlyPlayingViewModel
    fun mediaController(): MediaControllerCompat

    fun inject(activity: MainActivity)
    fun inject(activity: OnboardingActivity)
    fun inject(libraryFragment: LibraryFragment)
    fun inject(currentlyPlayingFragment: CurrentlyPlayingFragment)
}

