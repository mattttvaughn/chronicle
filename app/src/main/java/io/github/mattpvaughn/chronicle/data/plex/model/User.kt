package io.github.mattpvaughn.chronicle.data.plex.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class User(@Json(name = "authentication_token") val authToken: String = "")

@JsonClass(generateAdapter = true)
data class UserWrapper(@Json(name = "user") val user: User)
