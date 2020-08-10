package io.github.mattpvaughn.chronicle.injection.components

import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dagger.Component
import io.github.mattpvaughn.chronicle.application.MainActivity
import io.github.mattpvaughn.chronicle.application.MainActivityViewModel
import io.github.mattpvaughn.chronicle.data.ICachedFileManager
import io.github.mattpvaughn.chronicle.features.bookdetails.AudiobookDetailsFragment
import io.github.mattpvaughn.chronicle.features.bookdetails.AudiobookDetailsViewModel
import io.github.mattpvaughn.chronicle.features.currentlyplaying.CurrentlyPlayingFragment
import io.github.mattpvaughn.chronicle.features.currentlyplaying.CurrentlyPlayingViewModel
import io.github.mattpvaughn.chronicle.features.home.HomeFragment
import io.github.mattpvaughn.chronicle.features.library.LibraryFragment
import io.github.mattpvaughn.chronicle.features.login.ChooseLibraryFragment
import io.github.mattpvaughn.chronicle.features.login.ChooseServerFragment
import io.github.mattpvaughn.chronicle.features.login.ChooseUserFragment
import io.github.mattpvaughn.chronicle.features.login.LoginFragment
import io.github.mattpvaughn.chronicle.features.player.MediaServiceConnection
import io.github.mattpvaughn.chronicle.features.player.ProgressUpdater
import io.github.mattpvaughn.chronicle.features.settings.SettingsFragment
import io.github.mattpvaughn.chronicle.features.settings.SettingsViewModel
import io.github.mattpvaughn.chronicle.features.sources.SourceManagerFragment
import io.github.mattpvaughn.chronicle.injection.modules.ActivityModule
import io.github.mattpvaughn.chronicle.injection.scopes.ActivityScope
import io.github.mattpvaughn.chronicle.navigation.Navigator

@ActivityScope
@Component(dependencies = [AppComponent::class], modules = [ActivityModule::class])
interface ActivityComponent {
    fun cachedFileManager(): ICachedFileManager
    fun navigator(): Navigator
    fun progressUpdater(): ProgressUpdater
    fun localBroadcastManager(): LocalBroadcastManager
    fun mediaServiceConnection(): MediaServiceConnection

    fun mainActivityViewModelFactory(): MainActivityViewModel.Factory
    fun currentPlayingViewModelFactory(): CurrentlyPlayingViewModel.Factory
    fun audiobookDetailsViewModelFactory(): AudiobookDetailsViewModel.Factory
    fun settingsViewModelFactory(): SettingsViewModel.Factory

    fun inject(activity: MainActivity)
    fun inject(libraryFragment: LibraryFragment)
    fun inject(detailsFragment: AudiobookDetailsFragment)
    fun inject(homeFragment: HomeFragment)
    fun inject(settingsFragment: SettingsFragment)
    fun inject(currentlyPlayingFragment: CurrentlyPlayingFragment)
    fun inject(sourceManagerFragment: SourceManagerFragment)
    fun inject(loginFragment: LoginFragment)
    fun inject(chooseLibraryFragment: ChooseLibraryFragment)
    fun inject(chooseUserFragment: ChooseUserFragment)
    fun inject(chooseServerActivity: ChooseServerFragment)
}

