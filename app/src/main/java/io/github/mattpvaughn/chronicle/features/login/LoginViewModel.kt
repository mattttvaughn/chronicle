package io.github.mattpvaughn.chronicle.features.login

import androidx.lifecycle.*
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.data.plex.IPlexLoginRepo
import kotlinx.coroutines.launch
import javax.inject.Inject

class LoginViewModel(private val plexLoginRepo: IPlexLoginRepo) : ViewModel() {

    private val _loginForm = MutableLiveData<LoginFormState>()
    val loginFormState: LiveData<LoginFormState> = _loginForm

    val isLoading = Transformations.map(plexLoginRepo.loginState) { loginState ->
        return@map loginState == IPlexLoginRepo.LoginState.AWAITING_LOGIN_RESULTS
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            plexLoginRepo.login(username, password)
        }
    }

    fun loginDataChanged(username: String, password: String) {
        if (!isUserNameValid(username)) {
            _loginForm.value = LoginFormState(usernameError = R.string.invalid_username)
        }
        if (!isPasswordValid(password)) {
            _loginForm.value = LoginFormState(passwordError = R.string.invalid_password)
        }
        if (isUserNameValid(username) && isPasswordValid(password)) {
            _loginForm.value = LoginFormState(isDataValid = true)
        }
    }

    // A placeholder username validation check
    private fun isUserNameValid(username: String): Boolean {
        return username.isNotBlank()
    }

    // A placeholder password validation check
    private fun isPasswordValid(password: String): Boolean {
        return password.length > 5
    }

    /** Data validation state of the login form */
    data class LoginFormState(
        val usernameError: Int? = null,
        val passwordError: Int? = null,
        val isDataValid: Boolean = false
    )

    /**
     * ViewHolder provider factory to instantiate LoginViewModel.
     * Required given LoginViewModel has a non-empty constructor
     */
    class Factory @Inject constructor(private val plexLoginRepo: IPlexLoginRepo) :
        ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
                return LoginViewModel(plexLoginRepo) as T
            }
            throw IllegalArgumentException("Unknown ViewHolder class")
        }
    }
}
