package io.github.mattpvaughn.chronicle.data.sources.plex.model

import android.os.Parcelable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.android.parcel.Parcelize

@JsonClass(generateAdapter = true)
@Parcelize
data class PlexUser(
    val id: Long = 0L,
    val uuid: String = "",
    val title: String = "",
    val username: String? = "",
    val thumb: String = "",
    val hasPassword: Boolean = true, // PIN REQUIRED IF TRUE
    val admin: Boolean = false,
    val guest: Boolean = false,
    val authToken: String? = ""
) : Parcelable

@JsonClass(generateAdapter = true)
data class UsersResponse(@Json(name = "users") val users: List<PlexUser>)
