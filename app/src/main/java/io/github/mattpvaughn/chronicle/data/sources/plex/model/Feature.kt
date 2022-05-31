package io.github.mattpvaughn.chronicle.data.sources.plex.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Feature(@Json(name = "Directory") val plexDirectories: List<PlexDirectory> = emptyList())
