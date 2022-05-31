package io.github.mattpvaughn.chronicle.data.sources.plex

import io.github.mattpvaughn.chronicle.data.sources.plex.model.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

const val PLEX_LOGIN_SERVICE_URL = "https://plex.tv"
const val PLACEHOLDER_URL = "https://fake-base-url-should-never-be-called.yyy"

interface PlexLoginService {
    @POST("https://plex.tv/api/v2/pins.json?strong=true")
    suspend fun postAuthPin(): OAuthResponse

    @GET("https://plex.tv/api/v2/pins/{id}.json")
    suspend fun getAuthPin(@Path("id") id: Long): OAuthResponse

    /** Fetches the users for the account associated with the auth token passed in via header */
    @GET("https://plex.tv/api/v2/home/users")
    suspend fun getUsersForAccount(): UsersResponse

    /** Picks the user associated with the auth*/
    @POST("https://plex.tv/api/v2/home/users/{uuid}/switch")
    suspend fun pickUser(
        @Path("uuid") id: String,
        @Query("pin") pin: String? = null
    ): PlexUser

    @GET("https://plex.tv/api/v2/resources")
    suspend fun resources(
        @Query("includeHttps") shouldIncludeHttps: Int = 1,
        @Query("includeRelay") shouldIncludeRelay: Int = 1
    ): List<PlexServer>
}

interface PlexMediaService {
    /** A basic check used to tell whether a server is online. Returns a lightweight response */
    @GET("{url}/identity")
    suspend fun checkServer(@Path("url", encoded = true) url: String): Response<PlexMediaContainer>

    @GET("/library/sections/{libraryId}/all?type=$MEDIA_TYPE_ALBUM")
    suspend fun retrieveAllAlbums(
        @Path("libraryId") libraryId: String
    ): PlexMediaContainerWrapper

    @GET("/library/sections/{libraryId}/all?type=$MEDIA_TYPE_ALBUM")
    suspend fun retrieveAlbumPage(
        @Path("libraryId") libraryId: String,
        @Query("X-Plex-Container-Start") containerStart: Int = 0,
        @Query("X-Plex-Container-Size") containerSize: Int = 100,
    ): PlexMediaContainerWrapper

    @GET("/library/metadata/{trackId}")
    suspend fun retrieveChapterInfo(
        @Path("trackId") trackId: Int,
        @Query("includeChapters") includeChapters: Int = 1
    ): PlexMediaContainerWrapper

    @GET("/library/metadata/{albumId}")
    suspend fun retrieveAlbum(
        @Path("albumId") albumId: Int,
        @Query("includeChapters") includeChapters: Int = 1
    ): PlexMediaContainerWrapper

    @GET("/library/metadata/{albumId}/children")
    suspend fun retrieveTracksForAlbum(@Path("albumId") albumId: Int): PlexMediaContainerWrapper

    @GET("/library/sections")
    suspend fun retrieveLibraries(): PlexMediaContainerWrapper

    @GET("{url}")
    @Streaming
    suspend fun retrieveStreamByFilePath(
        @Path(value = "url", encoded = true) url: String
    ): ResponseBody

    /** Sets a media item to "watched" in the server. Works for both tracks and albums */
    @GET("/:/scrobble")
    suspend fun watched(
        @Query("key") key: String,
        @Query("identifier") identifier: String = "com.plexapp.plugins.library"
    )

    /** Sets a media item to "unwatched" in the server. Works for both tracks and albums */
    @GET("/:/unscrobble")
    suspend fun unwatched(
        @Query("key") key: String,
        @Query("identifier") identifier: String = "com.plexapp.plugins.library"
    )

    /**
     * Updates the runtime of a media item with a corresponding [ratingKey] to [offset], the number
     * of milliseconds progress from the start of the media item
     */
    @GET("/:/timeline")
    suspend fun progress(
        @Query("ratingKey") ratingKey: String,
        @Query("time") offset: String,
        @Query("key") key: String,
        @Query("duration") duration: Long,
        @Query("state") playState: String,
        @Query("hasMDE") hasMde: Int,
        @Query("identifier") identifier: String = "com.plexapp.plugins.library",
        @Query("playbackTime") playbackTime: Long = 0L,
        @Query("playQueueItemId") playQueueItemId: Long = 0L
    )

    /**
     * Starts a media session (like you would see in the "activity dashboard" in the Plex web app
     * for a media item identified by a server and ID combination. This session can be updated using
     * the [progress] function
     *
     * Note: Initial exploration indicates that this call is required in order for [progress]
     * updates to register in Plex, although I haven't confirmed it 100%
     */
    @POST("/playQueues")
    suspend fun startMediaSession(
        /** [serverUri] is in the form: "server://<MACHINE_IDENTIFIER>/com.plexapp.plugins.library/library/metadata/<BOOK_ID> */
        @Query("uri") serverUri: String,
        @Query("type") mediaType: String = MediaType.AUDIO_STRING,
        @Query("repeat") shouldRepeat: Boolean = false,
        @Query("own") isOwnedByUser: Boolean = true,
        @Query("includeChapters") shouldIncludeChapters: Boolean = true
    )

    /** Loads all [MediaType.TRACK]s available in the server */
    @GET("/library/sections/{libraryId}/all?type=$MEDIA_TYPE_TRACK")
    suspend fun retrieveAllTracksInLibrary(@Path("libraryId") libraryId: String): PlexMediaContainerWrapper

    @GET("/library/sections/{libraryId}/all?type=$MEDIA_TYPE_TRACK")
    suspend fun retrieveTracksPaginated(
        @Path("libraryId") libraryId: String,
        @Query("X-Plex-Container-Start") containerStart: Int = 0,
        @Query("X-Plex-Container-Size") containerSize: Int = 100,
    ): PlexMediaContainerWrapper
}
