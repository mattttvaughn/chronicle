package io.github.mattpvaughn.chronicle.data.plex

import io.github.mattpvaughn.chronicle.data.plex.model.Connection
import io.github.mattpvaughn.chronicle.features.chooselibrary.LibraryModel
import io.github.mattpvaughn.chronicle.features.chooseserver.ServerModel

class FakePlexPrefsRepo : PlexPrefsRepo {
    override fun removeAuthToken() {}

    override fun getAuthToken(): String {
        return ""
    }

    override fun putAuthToken(value: String) {}

    override fun getLibrary(): LibraryModel? {
        return null
    }

    override fun putLibrary(value: LibraryModel) {}

    override fun removeLibraryName() {}

    override fun putServer(serverModel: ServerModel) {}

    override fun getServer(): ServerModel? {
        return null
    }

    override fun removeServer() {}

    override fun getLastSuccessfulConnection(): Connection? {
        return null
    }

    override fun putSuccessfulConnection(connection: Connection) {}

}