package io.github.mattpvaughn.chronicle.navigation

import androidx.fragment.app.FragmentManager
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.features.login.ChooseLibraryFragment
import io.github.mattpvaughn.chronicle.features.login.ChooseServerFragment
import io.github.mattpvaughn.chronicle.features.login.LoginFragment

class Navigator(private val fragmentManager: FragmentManager) {

    fun showLogin() {
        val frag = LoginFragment.newInstance()
        fragmentManager.beginTransaction().replace(R.id.onboarding_container, frag).commit()
    }

    fun showServerChooser() {
        val frag = ChooseServerFragment.newInstance()
        fragmentManager.beginTransaction().replace(R.id.onboarding_container, frag).commit()
    }

    fun showLibraryChooser() {
        val frag = ChooseLibraryFragment.newInstance()
        fragmentManager.beginTransaction().replace(R.id.onboarding_container, frag).commit()
    }

    fun showHome() {

    }


}