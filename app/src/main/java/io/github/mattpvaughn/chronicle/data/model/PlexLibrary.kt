package io.github.mattpvaughn.chronicle.data.model

import io.github.mattpvaughn.chronicle.data.sources.plex.model.MediaType

data class PlexLibrary(val name: String, val type: MediaType, val id: String)
