package io.github.mattpvaughn.chronicle.features.login

import android.app.Instrumentation
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.application.FullAppTest.Companion.VALID_PASSWORD
import io.github.mattpvaughn.chronicle.application.FullAppTest.Companion.VALID_USERNAME
import io.github.mattpvaughn.chronicle.application.TestChronicleApplication
import io.github.mattpvaughn.chronicle.injection.components.UITestAppComponent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test


class OnboardingActivityTest {

    @get:Rule
    var activityRule = ActivityTestRule(
        OnboardingActivity::class.java,
        true,  // initialTouchMode
        false
    )

    private lateinit var component: UITestAppComponent

    @Before
    fun exposeDependencies() {
        val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as TestChronicleApplication
        component = app.appComponent as UITestAppComponent
    }

    @ExperimentalCoroutinesApi
    @Test
    fun testNormalLoginFlow_noCredentialsStored() = runBlockingTest {
        activityRule.launchActivity(null)

        onView(withId(R.id.plex_login_title)).check(matches(ViewMatchers.isDisplayed()))

        onView(withId(R.id.username)).perform(typeText(VALID_USERNAME))
        onView(withId(R.id.password)).perform(typeText(VALID_PASSWORD))

        onView(withId(R.id.login)).check(matches(isEnabled()))
        onView(withId(R.id.login)).perform(click())

        // Ensure we navigate to chooseServerActivity
        onView(withId(R.id.choose_server_title)).check(matches(ViewMatchers.isDisplayed()))
    }

    @Test
    fun testLoginFlow_authTokenStored() {
        component.plexPrefs().putAuthToken("VALID AUTH TOKEN")

        activityRule.launchActivity(null)

        // Ensure we navigate to MainActivity
        onView(withId(R.id.choose_server_title)).check(matches(ViewMatchers.isDisplayed()))
    }

}