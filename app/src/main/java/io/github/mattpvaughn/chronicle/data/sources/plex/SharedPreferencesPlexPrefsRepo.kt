package io.github.mattpvaughn.chronicle.data.sources.plex

import android.annotation.SuppressLint
import android.content.SharedPreferences
import com.squareup.moshi.Moshi
import io.github.mattpvaughn.chronicle.data.model.PlexLibrary
import io.github.mattpvaughn.chronicle.data.model.ServerModel
import io.github.mattpvaughn.chronicle.data.sources.plex.model.Connection
import io.github.mattpvaughn.chronicle.data.sources.plex.model.MediaType
import io.github.mattpvaughn.chronicle.data.sources.plex.model.PlexUser
import java.util.*
import javax.inject.Inject
import kotlin.collections.HashSet

/** A interface for Plex exclusive preferences */
interface PlexPrefsRepo {
    /**
     * The active auth token for the active account/profile. A 20ish character string. Defaults to
     * empty string "" if user is not signed in
     */
    var accountAuthToken: String

    /** The active user profile */
    var user: PlexUser?

    /** The active plex library */
    var library: PlexLibrary?

    /** Reference to the connected server */
    var server: ServerModel?

    /**
     * Temporary id used by oAuth to identify the client. Provided by the server. Only valid for
     * a few minutes so no strong need to clear it after login
     */
    var oAuthTempId: Long

    /** Unique user id */
    val uuid: String

    /** Clear all preferences which are handled by PrefsRepo */
    fun clear()
}

/** An implementation of [PlexPrefsRepo] wrapping [SharedPreferences]. */
class SharedPreferencesPlexPrefsRepo @Inject constructor(
    private val prefs: SharedPreferences,
    private val moshi: Moshi
) : PlexPrefsRepo {

    private companion object {
        const val PREFS_AUTH_TOKEN_KEY = "auth_token"
        const val PREFS_LIBRARY_NAME_KEY = "library_name"
        const val PREFS_LIBRARY_ID_KEY = "library_id"
        const val PREFS_SERVER_NAME_KEY = "server_name"
        const val PREFS_SERVER_ACCESS_TOKEN = "server_token"
        const val PREFS_SERVER_IS_OWNED = "server_owned"
        const val PREFS_SERVER_ID_KEY = "server_id"
        const val PREFS_REMOTE_SERVER_CONNECTIONS_KEY = "remote_server_connections"
        const val PREFS_USER = "user"
        const val PREFS_LOCAL_SERVER_CONNECTIONS_KEY = "local_server_connections"
        const val PREFS_UUID_KEY = "uuid"
        const val PREFS_TEMP_ID = "id"
        const val NO_TEMP_ID_FOUND = -1L
    }

    override val uuid: String
        @SuppressLint("ApplySharedPref")
        get() {
            var tempUUID = getString(PREFS_UUID_KEY, "")
            if (tempUUID.isEmpty()) {
                val generatedUUID = UUID.randomUUID().toString()
                prefs.edit().putString(PREFS_UUID_KEY, generatedUUID).commit()
                tempUUID = generatedUUID
            }
            return tempUUID
        }

    override var accountAuthToken: String
        get() = getString(PREFS_AUTH_TOKEN_KEY)
        @SuppressLint("ApplySharedPref")
        set(value) {
            prefs.edit().putString(PREFS_AUTH_TOKEN_KEY, value).commit()
        }

    override var user: PlexUser?
        get() {
            val userString = prefs.getString(PREFS_USER, "")
            if (userString.isNullOrEmpty()) {
                return null
            }
            return moshi.adapter<PlexUser>(PlexUser::class.java).fromJson(userString)
        }
        @SuppressLint("ApplySharedPref")
        set(value) {
            if (value == null) {
                prefs.edit().remove(PREFS_USER).commit()
                return
            }
            val userString = moshi.adapter<PlexUser>(PlexUser::class.java).toJson(value)
            prefs.edit().putString(PREFS_USER, userString).commit()
        }

    override var library: PlexLibrary?
        get() {
            val name = getString(PREFS_LIBRARY_NAME_KEY)
            val id = getString(PREFS_LIBRARY_ID_KEY)
            if (name.isEmpty() || id.isEmpty()) {
                return null
            }
            return PlexLibrary(name, MediaType.ARTIST, id)
        }
        @SuppressLint("ApplySharedPref")
        set(value) {
            if (value == null) {
                prefs.edit()
                    .remove(PREFS_LIBRARY_ID_KEY)
                    .remove(PREFS_LIBRARY_NAME_KEY).commit()
                return
            }
            prefs.edit()
                .putString(PREFS_LIBRARY_NAME_KEY, value.name)
                .putString(PREFS_LIBRARY_ID_KEY, value.id).commit()
        }

    override var server: ServerModel?
        get() {
            val name = getString(PREFS_SERVER_NAME_KEY)
            val id = getString(PREFS_SERVER_ID_KEY)
            val token: String = getString(PREFS_SERVER_ACCESS_TOKEN)
            val owned: Boolean = prefs.getBoolean(PREFS_SERVER_IS_OWNED, true)

            val connections = getServerConnections()

            if (name.isEmpty() || token.isEmpty() || connections.isEmpty()) {
                return null
            }

            return ServerModel(name, connections, id, token, owned)
        }
        @SuppressLint("ApplySharedPref")
        set(value) {
            if (value == null) {
                prefs.edit()
                    .remove(PREFS_SERVER_ID_KEY)
                    .remove(PREFS_SERVER_ACCESS_TOKEN)
                    .remove(PREFS_SERVER_IS_OWNED)
                    .remove(PREFS_LOCAL_SERVER_CONNECTIONS_KEY)
                    .remove(PREFS_REMOTE_SERVER_CONNECTIONS_KEY)
                    .remove(PREFS_SERVER_NAME_KEY).commit()
                return
            }
            prefs.edit()
                .putString(PREFS_SERVER_NAME_KEY, value.name)
                .putString(PREFS_SERVER_ID_KEY, value.serverId)
                .putString(PREFS_SERVER_ACCESS_TOKEN, value.accessToken)
                .putBoolean(PREFS_SERVER_IS_OWNED, value.owned).commit()
            putConnections(value.connections)
        }

    private fun getServerConnections(): List<Connection> {
        val localServers = getStringSet(PREFS_LOCAL_SERVER_CONNECTIONS_KEY).toList()
        val remoteServers = getStringSet(PREFS_REMOTE_SERVER_CONNECTIONS_KEY).toList()

        val combinedList = (localServers union remoteServers).toList()
        return combinedList.map { Connection(it) }
    }


    // TODO: ensure this is only usable for a certain amount of time
    override var oAuthTempId: Long
        get() = prefs.getLong(PREFS_TEMP_ID, NO_TEMP_ID_FOUND)
        @SuppressLint("ApplySharedPref")
        set(value) {
            prefs.edit().putLong(PREFS_TEMP_ID, value).commit()
        }

    override fun clear() {
        server = null
        library = null
        user = null
        accountAuthToken = ""
    }

    @SuppressLint("ApplySharedPref")
    private fun putConnections(connections: List<Connection>) {
        prefs.edit()
            .putStringSet(
                PREFS_LOCAL_SERVER_CONNECTIONS_KEY,
                connections.map { connection -> connection.uri }.toSet()
            )
            .putStringSet(
                PREFS_REMOTE_SERVER_CONNECTIONS_KEY,
                connections.map { connection -> connection.uri }.toSet()
            ).commit()
    }

    private fun getStringSet(key: String): MutableSet<String> {
        return prefs.getStringSet(key, HashSet<String>()) ?: HashSet()
    }

    /**
     * Retrieve a string stored in shared preferences
     *
     * @param key the key of the item stored in preferences
     * @param defaultValue (optional) the value to return if the desired string cannot be found.
     *                     Defaults to the empty string
     *
     * @return the stored preference value corresponding to the [key] passed in. If there is no
     * corresponding value, return the default value provided
     *
     */
    private fun getString(key: String, defaultValue: String = ""): String {
        return prefs.getString(key, defaultValue) ?: defaultValue
    }
}
