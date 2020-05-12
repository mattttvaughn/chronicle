package io.github.mattpvaughn.chronicle.data.plex

import android.content.SharedPreferences
import io.github.mattpvaughn.chronicle.data.model.Library
import io.github.mattpvaughn.chronicle.data.model.ServerModel
import io.github.mattpvaughn.chronicle.data.plex.model.Connection
import io.github.mattpvaughn.chronicle.data.plex.model.MediaType
import javax.inject.Inject

/**
 * A interface for Plex exclusive preferences
 */
interface PlexPrefsRepo {
    /**
     * The auth token for the plex user currently logged in. A string like "wec2cCQRFN...QWdowi2" if
     * a user is logged in, or "" if they are not
     */
    fun getAuthToken(): String
    fun putAuthToken(value: String)
    fun removeAuthToken()

    // The active plex library
    fun getLibrary(): Library?
    fun putLibrary(value: Library)
    fun removeLibraryName()

    // Reference to the connected server
    fun putServer(serverModel: ServerModel)
    fun getServer(): ServerModel?
    fun removeServer()

    // Clear all preferences which are handled by PrefsRepo
    fun clear()
}

/**
 * An implementation of [PlexPrefsRepo] wrapping [SharedPreferences]
 */
class SharedPreferencesPlexPrefsRepo @Inject constructor(private val prefs: SharedPreferences) :
    PlexPrefsRepo {

    private val PREFS_AUTH_TOKEN_KEY = "auth_token"
    private val PREFS_LIBRARY_NAME_KEY = "library_name"
    private val PREFS_LIBRARY_ID_KEY = "library_id"
    private val PREFS_SERVER_NAME_KEY = "server_name"
    private val PREFS_SERVER_ID_KEY = "server_id"
    private val PREFS_REMOTE_SERVER_CONNECTIONS_KEY = "remote_server_connections"
    private val PREFS_MOST_RECENT_SUCCESSFUL_SERVER_CONNECTION_KEY =
        "most_recent_server_connections"
    private val PREFS_LOCAL_SERVER_CONNECTIONS_KEY = "local_server_connections"

    override fun getAuthToken(): String {
        return getString(PREFS_AUTH_TOKEN_KEY)
    }

    override fun putAuthToken(value: String) {
        putString(PREFS_AUTH_TOKEN_KEY, value)
    }

    override fun removeAuthToken() {
        prefs.edit().remove(PREFS_AUTH_TOKEN_KEY).apply()
    }

    override fun getLibrary(): Library? {
        val name = getString(PREFS_LIBRARY_NAME_KEY)
        val id = getString(PREFS_LIBRARY_ID_KEY)
        if (name.isEmpty() || id.isEmpty()) {
            return null
        }
        return Library(
            name,
            MediaType.ARTIST,
            id
        )
    }

    override fun putLibrary(value: Library) {
        putString(PREFS_LIBRARY_NAME_KEY, value.name)
        putString(PREFS_LIBRARY_ID_KEY, value.id)
    }

    override fun removeLibraryName() {
        prefs.edit().remove(PREFS_LIBRARY_NAME_KEY).apply()
    }

    override fun putServer(serverModel: ServerModel) {
        putString(PREFS_SERVER_NAME_KEY, serverModel.name)
        putString(PREFS_SERVER_ID_KEY, serverModel.serverId)
        putConnections(serverModel.connections)
    }

    override fun getServer(): ServerModel? {
        val name = getString(PREFS_SERVER_NAME_KEY)
        val id = getString(PREFS_SERVER_ID_KEY)
        val connections = getServerConnections()

        if (name.isEmpty() || connections.isEmpty()) {
            return null
        }

        return ServerModel(
            name,
            connections,
            id
        )
    }

    private fun getServerConnections(): List<Connection> {
        val localServers = getStringSet(PREFS_LOCAL_SERVER_CONNECTIONS_KEY).toList()
        val remoteServers = getStringSet(PREFS_REMOTE_SERVER_CONNECTIONS_KEY).toList()

        val combinedList = ArrayList<String>(localServers).apply { addAll(remoteServers) }
        return combinedList.map { str ->
            Connection(
                uri = str
            )
        }
    }


    override fun removeServer() {
        prefs.edit()
            .remove(PREFS_SERVER_NAME_KEY)
            .remove(PREFS_SERVER_ID_KEY)
            .remove(PREFS_REMOTE_SERVER_CONNECTIONS_KEY)
            .remove(PREFS_LOCAL_SERVER_CONNECTIONS_KEY)
            .apply()
    }

    override fun clear() {
        removeServer()
        removeAuthToken()
        removeLibraryName()
    }

    private fun putConnections(connections: List<Connection>) {
        prefs.edit().putStringSet(
            PREFS_LOCAL_SERVER_CONNECTIONS_KEY,
            connections.map { connection -> connection.uri }.toSet()
        ).apply()
        prefs.edit().putStringSet(
            PREFS_REMOTE_SERVER_CONNECTIONS_KEY,
            connections.map { connection -> connection.uri }.toSet()
        ).apply()
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


    /**
     * Store a [String] [value] in shared preferences corresponding to a provided [String] [key]
     *
     * @param key the key of the string to be stored
     * @param value the [String] value to be stored
     *
     */
    private fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}
