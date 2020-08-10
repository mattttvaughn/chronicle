package io.github.mattpvaughn.chronicle.features.login

import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.*
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import io.github.mattpvaughn.chronicle.data.sources.SourceManager
import io.github.mattpvaughn.chronicle.data.sources.demo.DemoMediaSource
import io.github.mattpvaughn.chronicle.data.sources.plex.IPlexLoginRepo
import io.github.mattpvaughn.chronicle.data.sources.plex.model.OAuthResponse
import io.github.mattpvaughn.chronicle.navigation.Navigator
import io.github.mattpvaughn.chronicle.util.BooleanPreferenceLiveData
import io.github.mattpvaughn.chronicle.util.Event
import io.github.mattpvaughn.chronicle.util.postEvent
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.ExperimentalTime


class LoginViewModel(
    private val plexLoginRepo: IPlexLoginRepo,
    private val sourceManager: SourceManager,
    sharedPrefs: SharedPreferences,
    private val navigator: Navigator
) : ViewModel() {

    class Factory @Inject constructor(
        private val plexLoginRepo: IPlexLoginRepo,
        private val sharedPrefs: SharedPreferences,
        private val sourceManager: SourceManager,
        private val navigator: Navigator
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
                return LoginViewModel(plexLoginRepo, sourceManager, sharedPrefs, navigator) as T
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

    val allowAuto = BooleanPreferenceLiveData(PrefsRepo.KEY_ALLOW_AUTO, false, sharedPrefs)

    fun loginWithPlexOAuth() {
        viewModelScope.launch(Injector.get().unhandledExceptionHandler()) {
            val pin = plexLoginRepo.postOAuthPin()
            _authEvent.postEvent(pin)
        }
    }

    fun makePlexOAuthLoginUrl(id: String, code: String): Uri {
        return plexLoginRepo.makeOAuthUrl(id, code)
    }

    /** Whether the custom tab has been launched to login */
    fun setLaunched(b: Boolean) {
        hasLaunched = b
    }

    @ExperimentalTime
    fun addDemoLibrary() {
        val demoMediaSource = DemoMediaSource(
            sourceManager.generateUniqueId(),
            Injector.get().application()
        )
        sourceManager.addSource(demoMediaSource)
        demoMediaSource.setup(navigator)
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
