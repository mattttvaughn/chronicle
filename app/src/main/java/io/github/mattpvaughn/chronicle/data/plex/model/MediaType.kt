package io.github.mattpvaughn.chronicle.data.plex.model

class MediaType(val id: Long, val typeString: String, val title: String, val element: String) {

    companion object {
        val PERSON = MediaType(7, "person", "Person", "directory")
        val ARTIST = MediaType(8, "artist", "Artist", "directory")
        val ALBUM = MediaType(9, "album", "Album", "directory")
        val TRACK = MediaType(10, "track", "MediaItemTrack", "audio")

        val AUDIO_STRING = "audio"
    }
}
