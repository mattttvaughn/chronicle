package io.github.mattpvaughn.chronicle.data.sources.plex

import io.github.mattpvaughn.chronicle.data.model.PlexLibrary
import io.github.mattpvaughn.chronicle.data.model.ServerModel
import io.github.mattpvaughn.chronicle.data.sources.plex.model.PlexUser
import javax.inject.Inject

/** A non-persisted implementation of [PlexPrefsRepo] */
class FakePlexPrefsRepo @Inject constructor() : PlexPrefsRepo {

    override var accountAuthToken: String = ""

    override var user: PlexUser? = null

    override var library: PlexLibrary? = null

    override var server: ServerModel? = null

    override var oAuthTempId: Long = 0L
    override val uuid: String = ""

    override fun clear() {
        TODO("NOT IMPLEMENTED")
    }

    companion object {
        const val VALID_AUTH_TOKEN = "0d8g93huwsdoij2cxxqw"
        const val INVALID_AUTH_TOKEN = ""
    }

}