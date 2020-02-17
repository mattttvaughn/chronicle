package io.github.mattpvaughn.chronicle.injection.components

import dagger.Component
import io.github.mattpvaughn.chronicle.application.MainActivityTest
import io.github.mattpvaughn.chronicle.injection.modules.TestAppModule
import javax.inject.Singleton

@Singleton
@Component(modules = [TestAppModule::class])
interface TestAppComponent : AppComponent {
    fun inject(mainActivityTest: MainActivityTest)
}
