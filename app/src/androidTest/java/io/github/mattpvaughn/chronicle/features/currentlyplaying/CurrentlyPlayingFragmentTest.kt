package io.github.mattpvaughn.chronicle.features.currentlyplaying

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.espresso.Espresso.onView
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CurrentlyPlayingFragmentTest {

    @Before
    fun setupDagger() {

    }

    @Test
    fun test() {

        // Create a graphical FragmentScenario for the TitleScreen
        val titleScenario = launchFragmentInContainer<CurrentlyPlayingFragment>()

        titleScenario.onFragment { fragment ->
//            onView(fragment)
        }
    }
}