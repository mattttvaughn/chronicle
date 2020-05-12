package io.github.mattpvaughn.chronicle.data.plex

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.github.mattpvaughn.chronicle.data.model.Library
import io.github.mattpvaughn.chronicle.data.model.ServerModel
import io.github.mattpvaughn.chronicle.data.plex.IPlexLoginRepo.LoginState
import io.github.mattpvaughn.chronicle.data.plex.IPlexLoginRepo.LoginState.*
import okhttp3.Credentials
import javax.inject.Inject

interface IPlexLoginRepo {

    /**
     * Set [loginState] to [AWAITING_LOGIN_RESULTS], check username/password with server, set
     * [loginState] to a [FAILED_TO_LOG_IN] or [LOGGED_IN_NO_SERVER_CHOSEN] depending on the
     * success of the query
     */
    suspend fun login(username: String, password: String)

    /**
     * Chooses a server, sets it in the [PlexPrefsRepo], changes state to
     * [LOGGED_IN_NO_LIBRARY_CHOSEN]
     */
    fun chooseServer(serverModel: ServerModel)

    /**
     * Chooses a library, sets it in the [PlexPrefsRepo], changes state to
     * [LOGGED_IN_FULLY]
     */
    fun chooseLibrary(library: Library)

    /** Determine the [LoginState]*/
    fun determineLoginState(): LoginState

    fun clear()

    val loginState: LiveData<LoginState>

    enum class LoginState {
        NOT_LOGGED_IN,
        FAILED_TO_LOG_IN,
        LOGGED_IN_NO_SERVER_CHOSEN,
        LOGGED_IN_NO_LIBRARY_CHOSEN,
        LOGGED_IN_FULLY,
        AWAITING_LOGIN_RESULTS
    }
}

/**
 * Responsible for querying network w/r/t network data, configuring network to use login data once
 * on login succeeds, and for saving login info via [PlexPrefsRepo] on success
 */
class PlexLoginRepo @Inject constructor(
    private val plexPrefsRepo: PlexPrefsRepo,
    private val plexLoginService: PlexLoginService,
    private val plexConfig: PlexConfig
) : IPlexLoginRepo {

    private var _loginState = MutableLiveData<LoginState>()
    override val loginState: LiveData<LoginState>
        get() = _loginState

    override suspend fun login(username: String, password: String) {
        try {
            _loginState.postValue(AWAITING_LOGIN_RESULTS)
            val user = plexLoginService.signIn(Credentials.basic(username, password))
            plexPrefsRepo.putAuthToken(user.user.authToken)
            Log.i(APP_NAME, "User is: $user")
            _loginState.postValue(LOGGED_IN_NO_SERVER_CHOSEN)
        } catch (e: Throwable) {
            Log.e(APP_NAME, "Failed to log in: $e")
            _loginState.postValue(FAILED_TO_LOG_IN)
        }
    }

    override fun chooseServer(serverModel: ServerModel) {
        plexPrefsRepo.putServer(serverModel)
        _loginState.postValue(LOGGED_IN_NO_LIBRARY_CHOSEN)
    }

    override fun chooseLibrary(library: Library) {
        plexPrefsRepo.putLibrary(library)
        _loginState.postValue(LOGGED_IN_FULLY)
    }

    init {
        _loginState.postValue(determineLoginState())
    }

    override fun determineLoginState(): LoginState {
        val noAuthToken = plexPrefsRepo.getAuthToken().isEmpty()
        val noServer = plexPrefsRepo.getServer() == null
        val noLibrary = plexPrefsRepo.getLibrary() == null
        when {
            noAuthToken -> {
                return NOT_LOGGED_IN
            }
            noServer -> {
                plexConfig.authToken = plexPrefsRepo.getAuthToken()
                return LOGGED_IN_NO_SERVER_CHOSEN
            }
            noLibrary -> {
                plexConfig.authToken = plexPrefsRepo.getAuthToken()
                return LOGGED_IN_NO_LIBRARY_CHOSEN
            }
            else -> {
                Log.i(APP_NAME, "Fully logged in branch, awaiting server checks")
                plexConfig.authToken = plexPrefsRepo.getAuthToken()
                plexConfig.libraryId = requireNotNull(plexPrefsRepo.getLibrary()).id
                return LOGGED_IN_FULLY
            }
        }
    }

    override fun clear() {
        _loginState.postValue(NOT_LOGGED_IN)
    }

}