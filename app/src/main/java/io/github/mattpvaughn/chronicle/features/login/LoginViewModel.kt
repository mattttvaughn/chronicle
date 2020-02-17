package io.github.mattpvaughn.chronicle.features.login

import android.util.Log
import android.util.Patterns
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME
import io.github.mattpvaughn.chronicle.data.plex.PlexLoginApi
import io.github.mattpvaughn.chronicle.data.plex.PlexPrefsRepo
import io.github.mattpvaughn.chronicle.data.plex.PlexRequestSingleton
import io.github.mattpvaughn.chronicle.features.login.LoginViewModel.LoginState.*
import kotlinx.coroutines.*
import okhttp3.Credentials

class LoginViewModel(
    private val prefsRepo: PlexPrefsRepo
) : ViewModel() {

    private val _loginForm = MutableLiveData<LoginFormState>()
    val loginFormState: LiveData<LoginFormState> = _loginForm

    private var _loginState = MutableLiveData<LoginState>(NOT_LOGGED_IN)
    val loginState: LiveData<LoginState>
        get() = _loginState

    enum class LoginState {
        NOT_LOGGED_IN,
        FAILED_TO_LOG_IN,
        LOGGED_IN_NO_SERVER_CHOSEN,
        LOGGED_IN_NO_LIBRARY_CHOSEN,
        LOGGED_IN_FULLY,
        AWAITING_LOGIN_RESULTS
    }

    init {
        // If the user has previously connected a server/library, use that
        val authToken = prefsRepo.getAuthToken()
        val server = prefsRepo.getServer()
        val library = prefsRepo.getLibrary()
        Log.i(APP_NAME, "Auth token is $authToken")
        Log.i(APP_NAME, "Server is $server")
        Log.i(APP_NAME, "Library is $library")
        when {
            authToken.isEmpty() -> {
                _loginState.postValue(NOT_LOGGED_IN)
            }
            server == null -> {
                PlexRequestSingleton.authToken = prefsRepo.getAuthToken()
                _loginState.postValue(LOGGED_IN_NO_SERVER_CHOSEN)
            }
            library == null -> {
                PlexRequestSingleton.authToken = prefsRepo.getAuthToken()
                _loginState.postValue(LOGGED_IN_NO_LIBRARY_CHOSEN)
            }
            else -> {
                Log.i(APP_NAME, "Fully logged in branch, awaiting server checks")
                PlexRequestSingleton.authToken = prefsRepo.getAuthToken()
                PlexRequestSingleton.libraryId = library.id
                _loginState.postValue(LOGGED_IN_FULLY)
            }
        }
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            try {
                _loginState.postValue(AWAITING_LOGIN_RESULTS)
                val user =
                    PlexLoginApi.retrofitService.signIn(Credentials.basic(username, password))
                prefsRepo.putAuthToken(user.authToken)
                Log.i(APP_NAME, "User is: $user")
                _loginState.postValue(LOGGED_IN_NO_SERVER_CHOSEN)
            } catch (e: Throwable) {
                Log.e(APP_NAME, "Failed to log in: $e")
                _loginState.postValue(FAILED_TO_LOG_IN)
            }
        }
    }

    fun loginDataChanged(username: String, password: String) {
        if (!isUserNameValid(username)) {
            _loginForm.value = LoginFormState(usernameError = R.string.invalid_username)
        } else if (!isPasswordValid(password)) {
            _loginForm.value = LoginFormState(passwordError = R.string.invalid_password)
        } else {
            _loginForm.value = LoginFormState(isDataValid = true)
        }
    }

    // A placeholder username validation check
    private fun isUserNameValid(username: String): Boolean {
        return if (username.contains('@')) {
            Patterns.EMAIL_ADDRESS.matcher(username).matches()
        } else {
            username.isNotBlank()
        }
    }

    // A placeholder password validation check
    private fun isPasswordValid(password: String): Boolean {
        return password.length > 5
    }
}
