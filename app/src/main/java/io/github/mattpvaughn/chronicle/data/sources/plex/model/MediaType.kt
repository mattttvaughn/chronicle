package io.github.mattpvaughn.chronicle.data.sources.plex.model

class MediaType(val id: Long, val typeString: String, val title: String) {

    companion object {
        val PERSON = MediaType(7, "person", "Person")

        // NOTE: Music/audiobook libraries default to a type of ARTIST
        val ARTIST = MediaType(8, "artist", "Artist")
        val ALBUM = MediaType(9, "album", "Album")
        val TRACK = MediaType(10, "track", "MediaItemTrack")
        val FOLDER = MediaType(-1, "folder", "Folder")
        val COLLECTION = MediaType(-1, "collection", "Collection")

        val TYPES = listOf(PERSON, ARTIST, ALBUM, TRACK, FOLDER, COLLECTION)

        const val AUDIO_STRING = "audio"
    }
}

object MediaTypes {
    val ARTIST = MediaType.ARTIST
    val ALBUM = MediaType.ALBUM
    val TRACK = MediaType.TRACK
    val FOLDER = MediaType.FOLDER
    val COLLECTION = MediaType.COLLECTION
}
