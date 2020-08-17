package io.github.mattpvaughn.chronicle.data.sources.plex

import android.net.Uri
import io.github.mattpvaughn.chronicle.data.model.PlexLibrary
import io.github.mattpvaughn.chronicle.data.model.ServerModel
import io.github.mattpvaughn.chronicle.data.sources.plex.IPlexLoginRepo.LoginState
import io.github.mattpvaughn.chronicle.data.sources.plex.IPlexLoginRepo.LoginState.*
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexInterceptor.Companion.PLATFORM
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexInterceptor.Companion.PRODUCT
import io.github.mattpvaughn.chronicle.data.sources.plex.model.OAuthResponse
import io.github.mattpvaughn.chronicle.data.sources.plex.model.PlexUser
import io.github.mattpvaughn.chronicle.data.sources.plex.model.UsersResponse
import timber.log.Timber

interface IPlexLoginRepo {

    /** POST the OAuth pin to start OAuth login process, and retrieve a default response */
    suspend fun postOAuthPin(): OAuthResponse?

    /**
     * Chooses a user, updates the auth token to match the user in the [PlexLibrarySource], sets
     * [PlexPrefsRepo.user] to [responseUser]
     *
     * [responseUser] must have a valid auth token
     */
    fun chooseUser(responseUser: PlexUser)

    /** Chooses a server, sets it in the [PlexPrefsRepo] */
    fun chooseServer(serverModel: ServerModel)

    /** Chooses a library, sets it in the [PlexPrefsRepo], changes state to [LOGGED_IN_FULLY] */
    fun chooseLibrary(plexLibrary: PlexLibrary)

    enum class LoginState {
        NOT_LOGGED_IN,
        FAILED_TO_LOG_IN,
        LOGGED_IN_NO_USER_CHOSEN,
        LOGGED_IN_NO_SERVER_CHOSEN,
        LOGGED_IN_NO_LIBRARY_CHOSEN,
        LOGGED_IN_FULLY
    }

    fun makeOAuthUrl(clientId: String, code: String): Uri
    suspend fun checkForOAuthAccessToken(): Result<LoginState>
    fun determineLoginState(): LoginState
}

/**
 * Responsible for querying network w/r/t network data, configuring network to use login data once
 * on login succeeds, and for saving login info via [PlexPrefsRepo] on success
 */
class PlexLoginRepo(
    private val plexPrefsRepo: PlexPrefsRepo,
    private val plexLoginService: PlexLoginService
) : IPlexLoginRepo {

    override suspend fun postOAuthPin(): OAuthResponse? {
        return try {
            val pin = plexLoginService.postAuthPin()
            plexPrefsRepo.oAuthTempId = pin.id
            pin
        } catch (e: Throwable) {
            Timber.e("Failed to log in: $e")
            null
        }
    }

    override fun chooseUser(responseUser: PlexUser) {
        check(!responseUser.authToken.isNullOrEmpty())
        plexPrefsRepo.user = responseUser
    }

    override fun makeOAuthUrl(clientId: String, code: String): Uri {
        // Keep [ and ] characters for readability, replace with escaped chars below
        return Uri.parse(
            ("https://app.plex.tv/auth#?code=$code"
                    + "&context[device][product]=$PRODUCT"
                    + "&context[device][environment]=bundled"
                    + "&context[device][layout]=desktop"
                    + "&context[device][platform]=$PLATFORM"
                    + "&context[device][device]=$PRODUCT"
                    + "&clientID=$clientId")
                .replace("[", "%5B")
                .replace("]", "%5D")
        )
    }

    override suspend fun checkForOAuthAccessToken(): Result<LoginState> {
        val authToken = try {
            plexLoginService.getAuthPin(plexPrefsRepo.oAuthTempId).authToken ?: ""
        } catch (t: Throwable) {
            plexPrefsRepo.oAuthTempId = -1L
            Timber.i("Failed to get OAuth access token: ${t.message}")
            return Result.failure(t)
        }

        if (authToken.isEmpty()) {
            return Result.failure(Exception("No auth token found"))
        }
        plexPrefsRepo.accountAuthToken = authToken

        // now check if we should show user screen:
        return try {
            val userResponse: UsersResponse = plexLoginService.getUsersForAccount()
            if (userResponse.users.size == 1) {
                // if there is only one user, there's no need to choose it
                chooseUser(userResponse.users[0])
                Result.success(LOGGED_IN_NO_LIBRARY_CHOSEN)
            } else {
                // now we proceed to choose user
                Result.success(LOGGED_IN_NO_USER_CHOSEN)
            }
        } catch (t: Throwable) {
            Timber.e(t, "Failed to load users, cannot proceed to profile")
            Result.failure(t)
        }
    }

    override fun chooseServer(serverModel: ServerModel) {
        Timber.i("Chose server: $serverModel")
        plexPrefsRepo.server = serverModel
    }

    override fun chooseLibrary(plexLibrary: PlexLibrary) {
        plexPrefsRepo.library = plexLibrary
    }

    override fun determineLoginState(): LoginState {
        val token = plexPrefsRepo.accountAuthToken
        val user: PlexUser? = plexPrefsRepo.user
        val server: ServerModel? = plexPrefsRepo.server
        val library: PlexLibrary? = plexPrefsRepo.library
        Timber.i(
            """Login state: token = $token,
                    |user token = ${user?.authToken},
                    |server token = ${server?.accessToken},
                    |library = ${library?.name}""".trimMargin()
        )
        return when {
            token.isEmpty() -> NOT_LOGGED_IN
            server != null && library != null -> LOGGED_IN_FULLY // Migrating from v0.41, impossible otherwise
            user == null -> LOGGED_IN_NO_USER_CHOSEN
            server == null -> LOGGED_IN_NO_SERVER_CHOSEN
            library == null -> LOGGED_IN_NO_LIBRARY_CHOSEN
            else -> {
                Timber.i("Fully logged in branch, awaiting server checks")
                LOGGED_IN_FULLY
            }
        }
    }
}