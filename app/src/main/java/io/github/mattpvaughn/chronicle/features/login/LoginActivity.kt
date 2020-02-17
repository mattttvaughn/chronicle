package io.github.mattpvaughn.chronicle.features.login

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.application.MainActivity
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME
import io.github.mattpvaughn.chronicle.features.chooselibrary.ChooseLibraryActivity
import io.github.mattpvaughn.chronicle.features.chooseserver.ChooseServerActivity
import io.github.mattpvaughn.chronicle.features.login.LoginViewModel.LoginState.*


class LoginActivity : AppCompatActivity() {

    private lateinit var loginViewModel: LoginViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = Injector.get().plexPrefs()
        loginViewModel =
            ViewModelProvider(this, LoginViewModelFactory(prefs)).get(LoginViewModel::class.java)

        setContentView(R.layout.activity_login)

        val username = findViewById<EditText>(R.id.username)
        val password = findViewById<EditText>(R.id.password)
        val login = findViewById<Button>(R.id.login)
        val loading = findViewById<ProgressBar>(R.id.loading)

        loginViewModel.loginFormState.observe(this@LoginActivity, Observer {
            val loginState = it ?: return@Observer

            // disable login button unless both username / password is valid
            login.isEnabled = loginState.isDataValid

            if (loginState.usernameError != null) {
                username.error = getString(loginState.usernameError)
            }
            if (loginState.passwordError != null) {
                password.error = getString(loginState.passwordError)
            }
        })

        loginViewModel.loginState.observe(this, Observer { state ->
            when (state) {
                AWAITING_LOGIN_RESULTS -> {
                    loading.visibility = View.VISIBLE
                }
                null -> {
                    throw IllegalStateException("Login state cannot be null!")
                }
                else -> {
                    loading.visibility = View.GONE
                }
            }
            Log.i(APP_NAME, "Login state changed to $state")
            when (state) {
                LOGGED_IN_NO_SERVER_CHOSEN -> {
                    startActivity(Intent(this, ChooseServerActivity::class.java))
                }
                LOGGED_IN_NO_LIBRARY_CHOSEN -> {
                    startActivity(Intent(this, ChooseLibraryActivity::class.java))
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
                NOT_LOGGED_IN, AWAITING_LOGIN_RESULTS -> { /* Do nothing? */
                }
                else -> throw NoWhenBranchMatchedException("Unknown login state: $state")
            }
        })

        username.afterTextChanged {
            loginViewModel.loginDataChanged(
                username.text.toString(),
                password.text.toString()
            )
        }

        password.apply {
            afterTextChanged {
                loginViewModel.loginDataChanged(
                    username.text.toString(),
                    password.text.toString()
                )
            }

            setOnEditorActionListener { _, actionId, _ ->
                when (actionId) {
                    EditorInfo.IME_ACTION_DONE ->
                        loginViewModel.login(
                            username.text.toString(),
                            password.text.toString()
                        )
                }
                false
            }

            login.setOnClickListener {
                loading.visibility = View.VISIBLE
                loginViewModel.login(username.text.toString(), password.text.toString())
            }
        }
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
            afterTextChanged.invoke(editable.toString())
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
    })
}

