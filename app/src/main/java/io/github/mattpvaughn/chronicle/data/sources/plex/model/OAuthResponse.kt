package io.github.mattpvaughn.chronicle.data.sources.plex.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OAuthResponse(
    val id: Long,
    val clientIdentifier: String,
    val code: String,
    val authToken: String? = null
)
