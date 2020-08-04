package io.github.mattpvaughn.chronicle.features.login

import android.net.Uri
import androidx.lifecycle.*
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.data.sources.plex.IPlexLoginRepo
import io.github.mattpvaughn.chronicle.data.sources.plex.model.OAuthResponse
import io.github.mattpvaughn.chronicle.util.Event
import io.github.mattpvaughn.chronicle.util.postEvent
import kotlinx.coroutines.launch
import javax.inject.Inject


class LoginViewModel(private val plexLoginRepo: IPlexLoginRepo) : ViewModel() {

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


    private var _authEvent = MutableLiveData<Event<OAuthResponse?>>()
    val authEvent: LiveData<Event<OAuthResponse?>>
        get() = _authEvent

    private var hasLaunched = false

    val isLoading = Transformations.map(plexLoginRepo.loginEvent) { loginState ->
        return@map loginState.peekContent() == IPlexLoginRepo.LoginState.AWAITING_LOGIN_RESULTS
    }

    fun loginWithOAuth() {
        viewModelScope.launch(Injector.get().unhandledExceptionHandler()) {
            val pin = plexLoginRepo.postOAuthPin()
            _authEvent.postEvent(pin)
        }
    }

    fun makeOAuthLoginUrl(id: String, code: String): Uri {
        return plexLoginRepo.makeOAuthUrl(id, code)
    }

    /** Whether the custom tab has been launched to login */
    fun setLaunched(b: Boolean) {
        hasLaunched = b
    }

    fun checkForAccess() {
        if (hasLaunched) {
            viewModelScope.launch(Injector.get().unhandledExceptionHandler()) {
                // Check for access, if the login repo gains access, then our observer in
                // MainActivity will handle navigation
                plexLoginRepo.checkForOAuthAccessToken()
            }
        }
    }
}
