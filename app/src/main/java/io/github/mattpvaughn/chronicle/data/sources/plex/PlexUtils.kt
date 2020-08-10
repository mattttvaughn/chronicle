package io.github.mattpvaughn.chronicle.data.sources.plex

/**
 * Creates a URI uniquely identifying a media item with id [mediaId] on a server with machine
 * identifier [machineIdentifier]
 */
fun getMediaItemUri(machineIdentifier: String, mediaId: String): String {
    return "server://$machineIdentifier/com.plexapp.plugins.library/library/metadata/$mediaId"
}

