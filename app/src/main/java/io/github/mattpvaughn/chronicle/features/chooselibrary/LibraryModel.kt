package io.github.mattpvaughn.chronicle.features.chooselibrary

import io.github.mattpvaughn.chronicle.data.plex.model.MediaContainer
import io.github.mattpvaughn.chronicle.data.plex.model.MediaType
import io.github.mattpvaughn.chronicle.data.plex.model.MediaType.Companion.ARTIST

data class LibraryModel(val name: String, val type: MediaType, val id: String)

fun MediaContainer.asAudioLibraries(): List<LibraryModel> {
    return directories.filter {
        it.type == ARTIST.typeString
    }.map { directory -> LibraryModel(directory.title, ARTIST, directory.key) }
}