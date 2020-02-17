package io.github.mattpvaughn.chronicle.features.currentlyplaying

import android.util.Log
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME

class TestCurrentlyPlayingFragment(): CurrentlyPlayingFragment() {

    override fun injectMembers() {
        Log.i(APP_NAME, "Injecting members! activitycomponent should be set here!")
        activityComponent = FakeActivityComponent()
        viewModelFactory = CurrentlyPlayingViewModel.Factory(
            Injector.get().bookRepo(),
            Injector.get().trackRepo(),
            Injector.get().plexPrefs(),
            Injector.get().prefsRepo(),
            activityComponent.mediaServiceConnection()
        )
    }
}