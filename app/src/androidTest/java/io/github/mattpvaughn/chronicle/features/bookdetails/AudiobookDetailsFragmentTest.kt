package io.github.mattpvaughn.chronicle.features.bookdetails

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.features.currentlyplaying.CurrentlyPlayingFragment
import org.junit.Test

class AudiobookDetailsFragmentTest {


    @Test
    fun testNavigationToInGameScreen() {
        // Create a TestNavHostController

        val navController = TestNavHostController(
            ApplicationProvider.getApplicationContext())
        navController.setGraph(R.navigation.graph)

        // Create a graphical FragmentScenario for the TitleScreen
        val titleScenario = launchFragmentInContainer<CurrentlyPlayingFragment>()

        // Set the NavController property on the fragment
        titleScenario.onFragment { fragment ->
            Navigation.setViewNavController(fragment.requireView(), navController)
        }

        // Verify that performing a click changes the NavController’s state
//        onView(ViewMatchers.withId(R.id.play_btn)).perform(ViewActions.click())
//        assertThat(navController.currentDestination?.id).isEqualTo(R.id.in_game)
    }

}