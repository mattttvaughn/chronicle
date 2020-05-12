package io.github.mattpvaughn.chronicle.application

import android.app.Instrumentation
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.data.plex.model.*
import io.github.mattpvaughn.chronicle.features.login.LibraryListAdapter
import io.github.mattpvaughn.chronicle.features.login.OnboardingActivity
import io.github.mattpvaughn.chronicle.features.login.ServerListAdapter
import io.github.mattpvaughn.chronicle.injection.components.AppComponent
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Runs a basic espresso test covering basic usage of all screens of the app
 *
 * While this is certainly not ideal w/r/t full code coverage of the app and w/r/t changes in
 * integration and app behavior (e.g. this test will need frequent updating), it seems like it will
 * provide a lot of value for not a lot of work
 */
class FullAppTest {

    companion object {
        const val VALID_USERNAME = "username"
        const val VALID_PASSWORD = "password"
    }

    private val fakeUser = User("token")
    private val fakeConnection = Connection("fake-uri://", 1)
    private val fakeServerMediaContainer: MediaContainer = MediaContainer(
        devices = listOf(
            Server(
                name = "Fake server",
                provides = "server",
                connections = listOf(fakeConnection)
            )
        )
    )

    private val fakeLibraryMediaContainer: MediaContainer = MediaContainer(
        directories = listOf(Directory(title = "Fake library", key = "9"))
    )

    @get:Rule
    var activityRule = ActivityTestRule(
        OnboardingActivity::class.java,
        true,  // initialTouchMode
        false
    )

    private lateinit var component: AppComponent

    @Before
    fun setUp() = MockKAnnotations.init(this)

    @Before
    fun exposeDependencies() {
        val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as TestChronicleApplication
        component = app.appComponent
        component.plexPrefs().clear()
        component.prefsRepo().clearAll()
        coEvery { component.plexLoginService().resources() } returns fakeServerMediaContainer
        coEvery { component.plexLoginService().signIn(any()) } returns fakeUser
        coEvery {
            component.plexMediaService().retrieveLibraries()
        } returns fakeLibraryMediaContainer
        // Any non-error result will work
        coEvery {
            component.plexMediaService().checkServer(any())
        } returns fakeLibraryMediaContainer
    }

    @Test
    fun testApp_normalFlow() {
        activityRule.launchActivity(null)
        login()
        chooseServer()
        chooseLibrary()
    }

    private fun login() {
        Espresso.onView(ViewMatchers.withId(R.id.plex_login_title))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))

        Espresso.onView(ViewMatchers.withId(R.id.username))
            .perform(ViewActions.typeText(VALID_USERNAME))
        Espresso.onView(ViewMatchers.withId(R.id.password))
            .perform(ViewActions.typeText(VALID_PASSWORD))

        Espresso.onView(ViewMatchers.withId(R.id.login))
            .check(ViewAssertions.matches(ViewMatchers.isEnabled()))
        Espresso.onView(ViewMatchers.withId(R.id.login)).perform(ViewActions.click())

        // Ensure we navigate to chooseServerActivity
        Espresso.onView(ViewMatchers.withId(R.id.choose_server_title))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }

    private fun chooseServer() {
        Espresso.onView(ViewMatchers.withId(R.id.server_list)).perform(
            RecyclerViewActions.actionOnItemAtPosition<ServerListAdapter.ServerViewHolder>(
                0,
                click()
            )
        )
    }

    private fun chooseLibrary() {
        Espresso.onView(ViewMatchers.withId(R.id.library_list)).perform(
            RecyclerViewActions.actionOnItemAtPosition<LibraryListAdapter.LibraryViewHolder>(
                0,
                click()
            )
        )
    }
}