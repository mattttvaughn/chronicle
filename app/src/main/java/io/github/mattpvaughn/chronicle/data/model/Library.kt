package io.github.mattpvaughn.chronicle.data.model

import io.github.mattpvaughn.chronicle.data.plex.model.MediaType

data class Library(val name: String, val type: MediaType, val id: String)
