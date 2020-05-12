package io.github.mattpvaughn.chronicle.features.login

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.application.ChronicleApplication
import io.github.mattpvaughn.chronicle.application.MainActivity
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME
import io.github.mattpvaughn.chronicle.data.plex.IPlexLoginRepo
import io.github.mattpvaughn.chronicle.data.plex.IPlexLoginRepo.LoginState.*
import io.github.mattpvaughn.chronicle.data.plex.PlexPrefsRepo
import io.github.mattpvaughn.chronicle.injection.components.DaggerActivityComponent
import io.github.mattpvaughn.chronicle.injection.modules.ActivityModule
import io.github.mattpvaughn.chronicle.navigation.Navigator
import javax.inject.Inject


class OnboardingActivity : AppCompatActivity() {

    @Inject
    lateinit var plexPrefs: PlexPrefsRepo

    @Inject
    lateinit var plexLoginRepo: IPlexLoginRepo

    @Inject
    lateinit var navigator: Navigator

    override fun onCreate(savedInstanceState: Bundle?) {
        val activityComponent = DaggerActivityComponent.builder()
            .appComponent((application as ChronicleApplication).appComponent)
            .activityModule(ActivityModule(this))
            .build()
        activityComponent.inject(this)

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_onboarding)

        plexLoginRepo.loginState.observe(this, Observer { state ->
            Log.i(APP_NAME, "Login state changed to $state")
            when (state) {
                LOGGED_IN_NO_SERVER_CHOSEN -> {
                    navigator.showServerChooser()
                }
                LOGGED_IN_NO_LIBRARY_CHOSEN -> {
                    navigator.showLibraryChooser()
                }
                LOGGED_IN_FULLY -> {
                    val intentNoReturn = Intent(this, MainActivity::class.java)
                    intentNoReturn.flags =
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intentNoReturn)
                }
                FAILED_TO_LOG_IN -> {
                    showLoginFailed()
                }
                NOT_LOGGED_IN -> {
                    navigator.showLogin()
                }
                AWAITING_LOGIN_RESULTS -> {
                }
                else -> throw NoWhenBranchMatchedException("Unknown login state: $state")
            }
        })
    }

    private fun showLoginFailed() {
        Toast.makeText(applicationContext, getString(R.string.login_failed), Toast.LENGTH_SHORT)
            .show()
    }
}

/**
 * Extension function to simplify setting an afterTextChanged action to EditText components.
 */
fun EditText.afterTextChanged(afterTextChanged: (String) -> Unit) {
    this.addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(editable: Editable?) {
            afterTextChanged(editable.toString())
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
    })
}

