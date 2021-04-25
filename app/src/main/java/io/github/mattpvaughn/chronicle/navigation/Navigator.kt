package io.github.mattpvaughn.chronicle.navigation

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexLoginRepo.Companion.ARG_ERROR_MESSAGE_NO_PLEX_SOURCE_FOUND
import io.github.mattpvaughn.chronicle.features.bookdetails.AudiobookDetailsFragment
import io.github.mattpvaughn.chronicle.features.bookdetails.AudiobookDetailsFragment.Companion.ARG_AUDIOBOOK_ID
import io.github.mattpvaughn.chronicle.features.bookdetails.AudiobookDetailsFragment.Companion.ARG_AUDIOBOOK_SOURCE_ID
import io.github.mattpvaughn.chronicle.features.bookdetails.AudiobookDetailsFragment.Companion.ARG_AUDIOBOOK_TITLE
import io.github.mattpvaughn.chronicle.features.bookdetails.AudiobookDetailsFragment.Companion.ARG_IS_AUDIOBOOK_CACHED
import io.github.mattpvaughn.chronicle.features.home.HomeFragment
import io.github.mattpvaughn.chronicle.features.library.LibraryFragment
import io.github.mattpvaughn.chronicle.features.login.AddSourceFragment
import io.github.mattpvaughn.chronicle.features.login.ChooseLibraryFragment
import io.github.mattpvaughn.chronicle.features.login.ChooseServerFragment
import io.github.mattpvaughn.chronicle.features.login.ChooseUserFragment
import io.github.mattpvaughn.chronicle.features.settings.SettingsFragment
import io.github.mattpvaughn.chronicle.features.sources.SourceManagerFragment
import io.github.mattpvaughn.chronicle.injection.scopes.ActivityScope
import timber.log.Timber
import javax.inject.Inject

/**
 * Handles navigation actions.
 *
 * If I add any more screens then migrating to Jetpack Navigation is probably the way to go. This
 * scales poorly with additional self-contained nav graphs like the login process
 *
 * TODO: handle multiple back stacks for the different bottom nav items?
 */
@ActivityScope
class Navigator @Inject constructor(
    private val fragmentManager: FragmentManager,
    activity: AppCompatActivity
) {

    fun showLogin() {
        val frag = AddSourceFragment.newInstance()
        fragmentManager.beginTransaction()
            .replace(R.id.fragNavHost, frag)
            .commit()
    }

    fun showUserChooser() {
        val frag = ChooseUserFragment.newInstance()
        fragmentManager.beginTransaction()
            .replace(R.id.fragNavHost, frag, ChooseUserFragment.TAG)
            .commit()
    }

    fun showServerChooser() {
        val frag = ChooseServerFragment.newInstance()
        fragmentManager.beginTransaction()
            .replace(R.id.fragNavHost, frag, ChooseServerFragment.TAG)
            .commit()
    }

    fun showLibraryChooser() {
        val frag = ChooseLibraryFragment.newInstance()
        fragmentManager.beginTransaction()
            .replace(R.id.fragNavHost, frag, ChooseLibraryFragment.TAG)
            .commit()
    }


    fun showHome() {
        clearBackStack()
        // don't re-add home frag if it's already showing
        if (isFragmentWithTagVisible(HomeFragment.TAG)) {
            return
        }
        val homeFragment = HomeFragment.newInstance()
        fragmentManager.beginTransaction()
            .replace(R.id.fragNavHost, homeFragment, HomeFragment.TAG)
            .commit()
    }

    fun showLoginError(message: String) {
        clearBackStack()
        // don't re-add home frag if it's already showing
        if (isFragmentWithTagVisible(HomeFragment.TAG)) {
            return
        }
        val sourceManagerFragment = SourceManagerFragment.newInstance()
        sourceManagerFragment.arguments?.putString(ARG_ERROR_MESSAGE_NO_PLEX_SOURCE_FOUND, message)
        fragmentManager.beginTransaction()
            .replace(R.id.fragNavHost, sourceManagerFragment)
            .addToBackStack(SourceManagerFragment.TAG)
            .commit()
    }

    private fun clearBackStack() {
        while (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStackImmediate()
        }
    }

    fun showLibrary() {
        clearBackStack()
        val libraryFragment = LibraryFragment.newInstance()
        fragmentManager.beginTransaction().replace(R.id.fragNavHost, libraryFragment).commit()
    }

    fun showSettings() {
        clearBackStack()
        val settingsFragment = SettingsFragment.newInstance()
        fragmentManager.beginTransaction().replace(R.id.fragNavHost, settingsFragment).commit()
    }

    fun showDetails(audiobookId: Int, audiobookTitle: String, isAudiobookCached: Boolean, sourceId: Long) {
        val detailsFrag = AudiobookDetailsFragment.newInstance().apply {
            if (arguments == null) {
                arguments = Bundle()
            }
            requireArguments().putInt(ARG_AUDIOBOOK_ID, audiobookId)
            requireArguments().putLong(ARG_AUDIOBOOK_SOURCE_ID, sourceId)
            requireArguments().putString(ARG_AUDIOBOOK_TITLE, audiobookTitle)
            requireArguments().putBoolean(ARG_IS_AUDIOBOOK_CACHED, isAudiobookCached)
        }
        fragmentManager.beginTransaction()
            .replace(R.id.fragNavHost, detailsFrag)
            .addToBackStack(AudiobookDetailsFragment.TAG)
            .commit()
    }

    fun showSourcesManager() {
        val sourceManagerFragment = SourceManagerFragment.newInstance()
        fragmentManager.beginTransaction()
            .replace(R.id.fragNavHost, sourceManagerFragment)
            .addToBackStack(SourceManagerFragment.TAG)
            .commit()
    }

    fun addSource() {
        val loginFragment = AddSourceFragment.newInstance()
        fragmentManager.beginTransaction()
            .replace(R.id.fragNavHost, loginFragment)
            .addToBackStack(AddSourceFragment.TAG)
            .commit()
    }

    /** Handle back presses. Return a boolean indicating whether the back press event was handled */
    fun onBackPressed(): Boolean {
        val wasBackPressHandled = when {
            isFragmentWithTagVisible(ChooseUserFragment.TAG) -> {
                val frag =
                    (fragmentManager.findFragmentByTag(ChooseUserFragment.TAG) as ChooseUserFragment)
                if (frag.isPinEntryScreenVisible()) {
                    // close pin screen if it's visible
                    frag.hidePinEntryScreen()
                } else {
                    showLogin()
                }
                true
            }
            else -> false
        }

        return if (wasBackPressHandled) {
            true
        } else {
            // If the navigator didn't handle the back press and there are fragments on the back
            // stack, pop a fragment off the back stack
            if (fragmentManager.backStackEntryCount == 0) {
                false
            } else {
                Timber.i("Popping backstack!")
                fragmentManager.popBackStack()
                true
            }
        }

    }

    private fun isFragmentWithTagVisible(tag: String): Boolean {
        return fragmentManager.findFragmentByTag(tag)?.isVisible == true
    }

}