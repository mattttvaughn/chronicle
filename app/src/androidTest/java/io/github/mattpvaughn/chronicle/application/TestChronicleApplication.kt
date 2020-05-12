package io.github.mattpvaughn.chronicle.application

import android.util.Log
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME
import io.github.mattpvaughn.chronicle.injection.components.AppComponent
import io.github.mattpvaughn.chronicle.injection.components.DaggerUITestAppComponent
import io.github.mattpvaughn.chronicle.injection.modules.UITestAppModule
import javax.inject.Singleton


@Singleton
class TestChronicleApplication : ChronicleApplication() {
    override fun initializeComponent(): AppComponent {
        Log.i(APP_NAME, "Test chronicle application component")
        return DaggerUITestAppComponent.builder()
            .uITestAppModule(UITestAppModule(applicationContext)).build()
    }
}