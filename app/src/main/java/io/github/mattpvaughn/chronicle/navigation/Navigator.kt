package io.github.mattpvaughn.chronicle.navigation

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.data.sources.plex.IPlexLoginRepo
import io.github.mattpvaughn.chronicle.data.sources.plex.IPlexLoginRepo.LoginState.*
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexConfig
import io.github.mattpvaughn.chronicle.features.bookdetails.AudiobookDetailsFragment
import io.github.mattpvaughn.chronicle.features.bookdetails.AudiobookDetailsFragment.Companion.ARG_AUDIOBOOK_ID
import io.github.mattpvaughn.chronicle.features.bookdetails.AudiobookDetailsFragment.Companion.ARG_AUDIOBOOK_TITLE
import io.github.mattpvaughn.chronicle.features.bookdetails.AudiobookDetailsFragment.Companion.ARG_IS_AUDIOBOOK_CACHED
import io.github.mattpvaughn.chronicle.features.collections.CollectionDetailsFragment
import io.github.mattpvaughn.chronicle.features.collections.CollectionsFragment
import io.github.mattpvaughn.chronicle.features.home.HomeFragment
import io.github.mattpvaughn.chronicle.features.library.LibraryFragment
import io.github.mattpvaughn.chronicle.features.login.ChooseLibraryFragment
import io.github.mattpvaughn.chronicle.features.login.ChooseServerFragment
import io.github.mattpvaughn.chronicle.features.login.ChooseUserFragment
import io.github.mattpvaughn.chronicle.features.login.LoginFragment
import io.github.mattpvaughn.chronicle.features.settings.SettingsFragment
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
    private val plexConfig: PlexConfig,
    plexLoginRepo: IPlexLoginRepo,
    activity: AppCompatActivity
) {

    init {
        // never remove observer, but this is a singleton so it's okay
        plexLoginRepo.loginEvent.observe(
            activity,
            Observer { event ->
                if (event.hasBeenHandled) {
                    return@Observer
                }
                Timber.i("Login event changed to ${event.peekContent()}")
                when (event.getContentIfNotHandled()) {
                    LOGGED_IN_NO_USER_CHOSEN -> showUserChooser()
                    LOGGED_IN_NO_SERVER_CHOSEN -> showServerChooser()
                    LOGGED_IN_NO_LIBRARY_CHOSEN -> showLibraryChooser()
                    LOGGED_IN_FULLY -> showHome()
                    FAILED_TO_LOG_IN -> {
                    }
                    NOT_LOGGED_IN -> showLogin()
                    AWAITING_LOGIN_RESULTS -> {
                    }
                    else -> throw NoWhenBranchMatchedException("Unknown login event: $event")
                }
            }
        )
    }

    fun showLogin() {
        plexConfig.clear()
        val frag = LoginFragment.newInstance()
        fragmentManager.beginTransaction()
            .replace(R.id.fragNavHost, frag)
            .commit()
    }

    fun showUserChooser() {
        plexConfig.clearServer()
        plexConfig.clearLibrary()
        plexConfig.clearUser()
        val frag = ChooseUserFragment.newInstance()
        fragmentManager.beginTransaction()
            .replace(R.id.fragNavHost, frag, ChooseUserFragment.TAG)
            .commit()
    }

    fun showServerChooser() {
        plexConfig.clearServer()
        plexConfig.clearLibrary()
        val frag = ChooseServerFragment.newInstance()
        fragmentManager.beginTransaction()
            .replace(R.id.fragNavHost, frag, ChooseServerFragment.TAG)
            .commit()
    }

    fun showLibraryChooser() {
        plexConfig.clearLibrary()
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

    fun showCollections() {
        clearBackStack()
        val collectionsFragment = CollectionsFragment.newInstance()
        fragmentManager.beginTransaction().replace(R.id.fragNavHost, collectionsFragment).commit()
    }

    fun showSettings() {
        clearBackStack()
        val settingsFragment = SettingsFragment.newInstance()
        fragmentManager.beginTransaction().replace(R.id.fragNavHost, settingsFragment).commit()
    }

    fun showDetails(audiobookId: Int, audiobookTitle: String, isAudiobookCached: Boolean) {
        val detailsFrag = AudiobookDetailsFragment.newInstance().apply {
            if (arguments == null) {
                arguments = Bundle()
            }
            requireArguments().putInt(ARG_AUDIOBOOK_ID, audiobookId)
            requireArguments().putString(ARG_AUDIOBOOK_TITLE, audiobookTitle)
            requireArguments().putBoolean(ARG_IS_AUDIOBOOK_CACHED, isAudiobookCached)
        }
        fragmentManager.beginTransaction()
            .replace(R.id.fragNavHost, detailsFrag)
            .addToBackStack(AudiobookDetailsFragment.TAG)
            .commit()
    }

    fun showCollectionDetails(collectionId: Int) {
        val collectionDetails = CollectionDetailsFragment.newInstance(collectionId)
        fragmentManager.beginTransaction()
            .replace(R.id.fragNavHost, collectionDetails)
            .addToBackStack(CollectionDetailsFragment.TAG)
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
            isFragmentWithTagVisible(ChooseServerFragment.TAG) -> {
                plexConfig.clearUser()
                showUserChooser()
                true
            }
            isFragmentWithTagVisible(ChooseLibraryFragment.TAG) -> {
                plexConfig.clearServer()
                showServerChooser()
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
