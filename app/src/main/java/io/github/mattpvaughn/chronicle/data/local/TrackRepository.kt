package io.github.mattpvaughn.chronicle.data.local

import androidx.lifecycle.LiveData
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import io.github.mattpvaughn.chronicle.data.model.NO_AUDIOBOOK_FOUND_ID
import io.github.mattpvaughn.chronicle.data.sources.MediaSource
import io.github.mattpvaughn.chronicle.data.sources.SourceManager
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexMediaService
import io.github.mattpvaughn.chronicle.data.sources.plex.model.MediaType
import io.github.mattpvaughn.chronicle.data.sources.plex.model.asTrackList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** A repository abstracting all [MediaItemTrack]s from all [MediaSource]s */
interface ITrackRepository {
    /**
     * Load all tracks from the network corresponding to the book with id == [bookId], add them to
     * the local [TrackDatabase], and return them
     */
    suspend fun loadTracksForAudiobook(bookId: Int): Result<List<MediaItemTrack>>

    /**
     * Update the value of [MediaItemTrack.cached] to [isCached] for a [MediaItemTrack] with
     * [MediaItemTrack.id] == [trackId] in the [TrackDatabase]
     */
    suspend fun updateCachedStatus(trackId: Int, isCached: Boolean)

    /** Return all tracks in the [TrackDatabase]  */
    fun getAllTracks(): LiveData<List<MediaItemTrack>>

    suspend fun getAllTracksAsync(): List<MediaItemTrack>

    /**
     * Return a [LiveData<List<MediaItemTrack>>] containing all [MediaItemTrack]s where
     * [MediaItemTrack.parentKey] == [bookId]
     */
    fun getTracksForAudiobook(bookId: Int): LiveData<List<MediaItemTrack>>

    suspend fun getTracksForAudiobookAsync(id: Int): List<MediaItemTrack>

    /** Update the value of [MediaItemTrack.progress] == [trackProgress] and
     * [MediaItemTrack.lastViewedAt] == [lastViewedAt] for the track where
     * [MediaItemTrack.id] == [trackId]
     */
    suspend fun updateTrackProgress(trackProgress: Long, trackId: Int, lastViewedAt: Long)

    /**
     * Return a [MediaItemTrack] where [MediaItemTrack.id] == [id], or null if no such
     * [MediaItemTrack] exists
     */
    suspend fun getTrackAsync(id: Int): MediaItemTrack?

    /**
     * Return the [MediaItemTrack.parentKey] for a [MediaItemTrack] where [MediaItemTrack.id] == [trackId]
     */
    suspend fun getBookIdForTrack(trackId: Int): Int

    /** Remove all [MediaItemTrack] from the [TrackDatabase] */
    suspend fun clear()

    /**
     * Return a [List<MediaItemTrack>] containing all [MediaItemTrack] where [MediaItemTrack.cached] == true
     */
    suspend fun getCachedTracks(): List<MediaItemTrack>

    /** Returns the number of [MediaItemTrack] where [MediaItemTrack.parentKey] == [bookId] */
    suspend fun getTrackCountForBookAsync(bookId: Int): Int

    /**
     * Returns the number of [MediaItemTrack] where [MediaItemTrack.parentKey] == [bookId] and
     * [MediaItemTrack.cached] == true
     */
    suspend fun getCachedTrackCountForBookAsync(bookId: Int): Int

    /** Sets [MediaItemTrack.cached] to false for all [MediaItemTrack] in [TrackDatabase] */
    suspend fun uncacheAll()

    /** Fetches all [MediaType.TRACK]s from the server, updates the local db */
    suspend fun refreshData(): List<Result<Unit>>

    suspend fun findTrackByTitle(title: String): MediaItemTrack?

    companion object {
        /**
         * The value representing the [MediaItemTrack.id] for any track which does not exist in the
         * [TrackDatabase]
         */
        const val TRACK_NOT_FOUND: Int = -23
    }
}

@Singleton
class TrackRepository @Inject constructor(
    private val trackDao: TrackDao,
    private val prefsRepo: PrefsRepo,
    private val plexMediaService: PlexMediaService,
    private val sourceManager: SourceManager
) : ITrackRepository {

    @Throws(Throwable::class)
    override suspend fun refreshData(): List<Result<Unit>> {
        if (prefsRepo.offlineMode) {
            return listOf(Result.success(Unit))
        }
        return withContext(Dispatchers.IO) {
            val queryResults = sourceManager.fetchTracks()

            return@withContext queryResults.map { queryResult ->
                if (queryResult.second.isFailure) {
                    return@map Result.failure<Unit>(
                        queryResult.second.exceptionOrNull() ?: Exception()
                    )
                }
                val sourceId = queryResult.first
                val localTracks = trackDao.getAllTracksInSource(sourceId)
                val networkTracks = queryResult.second.getOrNull() ?: emptyList()
                val merged = mergeNetworkTracks(networkTracks, localTracks)
                trackDao.insertAll(merged)
                return@map Result.success(Unit)
            }
        }
    }

    override suspend fun findTrackByTitle(title: String): MediaItemTrack? {
        return withContext(Dispatchers.IO) {
            trackDao.findTrackByTitle(title)
        }
    }

    override suspend fun loadTracksForAudiobook(bookId: Int): Result<List<MediaItemTrack>> {
        return withContext(Dispatchers.IO) {
            val localTracks = trackDao.getAllTracksAsync()
            try {
                val networkTracks =
                    plexMediaService.retrieveTracksForAlbum(bookId).plexMediaContainer.asTrackList()

                val mergedTracks = mergeNetworkTracks(networkTracks, localTracks)
                trackDao.insertAll(mergedTracks)
                Result.success(mergedTracks)
            } catch (t: Throwable) {
                Result.failure<List<MediaItemTrack>>(t)
            }
        }
    }

    override suspend fun updateCachedStatus(trackId: Int, isCached: Boolean) {
        withContext(Dispatchers.IO) {
            trackDao.updateCachedStatus(trackId, isCached)
        }
    }

    override fun getAllTracks(): LiveData<List<MediaItemTrack>> {
        return trackDao.getAllTracks()
    }

    override suspend fun getAllTracksAsync(): List<MediaItemTrack> {
        return withContext(Dispatchers.IO) {
            trackDao.getAllTracksAsync()
        }
    }


    override fun getTracksForAudiobook(bookId: Int): LiveData<List<MediaItemTrack>> {
        return trackDao.getTracksForAudiobook(bookId, prefsRepo.offlineMode)
    }

    override suspend fun getTracksForAudiobookAsync(id: Int): List<MediaItemTrack> {
        return withContext(Dispatchers.IO) {
            trackDao.getTracksForAudiobookAsync(id, prefsRepo.offlineMode)
        }
    }

    override suspend fun updateTrackProgress(
        trackProgress: Long,
        trackId: Int,
        lastViewedAt: Long
    ) {
        withContext(Dispatchers.IO) {
            trackDao.updateProgress(
                trackProgress = trackProgress,
                trackId = trackId,
                lastViewedAt = lastViewedAt
            )
        }
    }

    override suspend fun getTrackAsync(id: Int): MediaItemTrack? {
        return trackDao.getTrackAsync(id)
    }

    override suspend fun getBookIdForTrack(trackId: Int): Int {
        return withContext(Dispatchers.IO) {
            val track = trackDao.getTrackAsync(trackId)
            Timber.i("Track is $track")
            val parentKey = track?.parentKey
            parentKey ?: NO_AUDIOBOOK_FOUND_ID
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            trackDao.clear()
        }
    }

    override suspend fun getCachedTracks(): List<MediaItemTrack> {
        return withContext(Dispatchers.IO) {
            trackDao.getCachedTracksAsync(isCached = true)
        }
    }

    override suspend fun getTrackCountForBookAsync(bookId: Int): Int {
        return withContext(Dispatchers.IO) {
            trackDao.getTrackCountForAudiobookAsync(bookId)
        }
    }

    override suspend fun getCachedTrackCountForBookAsync(bookId: Int): Int {
        return withContext(Dispatchers.IO) {
            trackDao.getCachedTrackCountForBookAsync(bookId)
        }
    }

    override suspend fun uncacheAll() {
        withContext(Dispatchers.IO) {
            trackDao.uncacheAll()
        }
    }


    /**
     * Merges a list of tracks from the network into the DB by comparing to local tracks and using
     * using [MediaItemTrack.merge] to determine which version to keep
     */
    private fun mergeNetworkTracks(
        networkTracks: List<MediaItemTrack>,
        localTracks: List<MediaItemTrack>
    ): List<MediaItemTrack> {
        return networkTracks.map { networkTrack ->
            val localTrack = localTracks.find { it.id == networkTrack.id }
            if (localTrack != null) {
                return@map MediaItemTrack.merge(network = networkTrack, local = localTrack)
            } else {
                return@map networkTrack
            }
        }
    }
}