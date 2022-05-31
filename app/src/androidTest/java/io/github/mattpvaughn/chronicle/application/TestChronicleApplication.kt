package io.github.mattpvaughn.chronicle.application

import io.github.mattpvaughn.chronicle.injection.components.AppComponent
import io.github.mattpvaughn.chronicle.injection.components.DaggerUITestAppComponent
import io.github.mattpvaughn.chronicle.injection.modules.UITestAppModule
import timber.log.Timber
import javax.inject.Singleton

@Singleton
class TestChronicleApplication : ChronicleApplication() {
    override fun initializeComponent(): AppComponent {
        Timber.i("Test chronicle application component")
        return DaggerUITestAppComponent.builder()
            .uITestAppModule(UITestAppModule(applicationContext)).build()
    }
}
