package io.github.mattpvaughn.chronicle.data.plex.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Feature(@Json(name = "Directory") val directories: List<Directory> = emptyList())