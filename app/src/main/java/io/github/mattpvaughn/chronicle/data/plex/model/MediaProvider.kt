package io.github.mattpvaughn.chronicle.data.plex.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MediaProvider(@Json(name = "Feature") val feature: List<Feature> = emptyList())