package io.github.mattpvaughn.chronicle.application

import io.github.mattpvaughn.chronicle.injection.components.AppComponent
import io.github.mattpvaughn.chronicle.injection.components.DaggerTestAppComponent
import javax.inject.Singleton


@Singleton
class TestChronicleApplication : ChronicleApplication() {
    override fun initializeComponent(): AppComponent {
        DaggerTestAppComponent.builder()
        return super.initializeComponent()
    }
}