package io.github.mattpvaughn.chronicle.data.plex

import io.github.mattpvaughn.chronicle.data.model.Library
import io.github.mattpvaughn.chronicle.data.model.ServerModel
import javax.inject.Inject

/** A non-persisted implementation of [PlexPrefsRepo] */
class FakePlexPrefsRepo @Inject constructor() : PlexPrefsRepo {
    private var token = ""

    override fun removeAuthToken() {
        token = ""
    }

    override fun getAuthToken(): String {
        return token
    }

    override fun putAuthToken(value: String) {
        token = value
    }

    private var library: Library? = null
    override fun getLibrary(): Library? {
        return library
    }

    override fun putLibrary(value: Library) {
        library = value
    }

    override fun removeLibraryName() {
        library = null
    }

    private var server: ServerModel? = null
    override fun putServer(serverModel: ServerModel) {
        server = serverModel
    }

    override fun getServer(): ServerModel? {
        return server
    }

    override fun removeServer() {
        server = null
    }

    override fun clear() {
        removeServer()
        removeAuthToken()
        removeLibraryName()
    }

    companion object {
        const val VALID_AUTH_TOKEN = "0d8g93huwsdoij2cxxqw"
        const val INVALID_AUTH_TOKEN = ""
    }

}