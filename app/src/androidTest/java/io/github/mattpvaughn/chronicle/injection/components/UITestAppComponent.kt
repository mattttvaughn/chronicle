package io.github.mattpvaughn.chronicle.injection.components

import dagger.Component
import io.github.mattpvaughn.chronicle.features.login.OnboardingActivityTest
import io.github.mattpvaughn.chronicle.injection.modules.UITestAppModule
import javax.inject.Singleton

@Component(modules = [UITestAppModule::class])
@Singleton
interface UITestAppComponent : AppComponent {
    // Inject
    fun inject(loginActivityTest: OnboardingActivityTest)
}
