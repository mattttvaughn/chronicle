package io.github.mattpvaughn.chronicle.features.login

import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.*
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import io.github.mattpvaughn.chronicle.data.sources.MediaSourceFactory
import io.github.mattpvaughn.chronicle.data.sources.SourceManager
import io.github.mattpvaughn.chronicle.data.sources.demo.DemoMediaSource
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexLibrarySource
import io.github.mattpvaughn.chronicle.data.sources.plex.model.OAuthResponse
import io.github.mattpvaughn.chronicle.navigation.Navigator
import io.github.mattpvaughn.chronicle.util.BooleanPreferenceLiveData
import io.github.mattpvaughn.chronicle.util.Event
import io.github.mattpvaughn.chronicle.util.postEvent
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.ExperimentalTime


class AddSourceViewModel(
    private val sourceManager: SourceManager,
    sharedPrefs: SharedPreferences,
    private val navigator: Navigator,
    private val mediaSourceFactory: MediaSourceFactory,
    private val potentialPlexSource: PlexLibrarySource
) : ViewModel() {

    class Factory @Inject constructor(
        private val sharedPrefs: SharedPreferences,
        private val sourceManager: SourceManager,
        private val navigator: Navigator,
        private val mediaSourceFactory: MediaSourceFactory
    ) : ViewModelProvider.Factory {

        lateinit var potentialPlexSource: PlexLibrarySource

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(this::potentialPlexSource.isInitialized) { "Source must be provided!" }
            if (modelClass.isAssignableFrom(AddSourceViewModel::class.java)) {
                return AddSourceViewModel(
                    sourceManager,
                    sharedPrefs,
                    navigator,
                    mediaSourceFactory,
                    potentialPlexSource
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewHolder class")
        }
    }

    private var _authEvent = MutableLiveData<Event<OAuthResponse?>>()
    val authEvent: LiveData<Event<OAuthResponse?>>
        get() = _authEvent

    private var _messageForUser = MutableLiveData<Event<String>>()
    val messageForUser: LiveData<Event<String>>
        get() = _messageForUser

    private var hasLaunched = false

    private var _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean>
        get() = _isLoading

    val allowAuto = BooleanPreferenceLiveData(PrefsRepo.KEY_ALLOW_AUTO, false, sharedPrefs)

    fun loginWithPlexOAuth() {
        viewModelScope.launch(Injector.get().unhandledExceptionHandler()) {
            val pin = potentialPlexSource.postOAuthPin()
            _authEvent.postEvent(pin)
        }
    }

    fun makePlexOAuthLoginUrl(id: String, code: String): Uri {
        return potentialPlexSource.makeOAuthUrl(id, code)
    }

    /** Whether the custom tab has been launched to login */
    fun setLaunched(b: Boolean) {
        hasLaunched = b
    }

    @ExperimentalTime
    fun addDemoLibrary() {
        val demoMediaSource = DemoMediaSource(
            sourceManager.generateUniqueId(),
            Injector.get().applicationContext()
        )
        sourceManager.addSource(demoMediaSource)
        demoMediaSource.setup(navigator)
    }

    fun checkForAccess() {
        if (hasLaunched) {
            viewModelScope.launch(Injector.get().unhandledExceptionHandler()) {
                // Check for access, if the login repo gains access, then PlexLibrary will handle
                // navigation until login
                sourceManager.addSource(
                    mediaSourceFactory.create(
                        sourceManager.generateUniqueId(),
                        PlexLibrarySource.TAG
                    )
                )
                val result = potentialPlexSource.checkForOAuthAccessToken(navigator)
                val errorMessage = result.exceptionOrNull()?.message
                if (errorMessage != null) {
                    _messageForUser.postEvent(errorMessage)
                }
            }
        }
    }
}
