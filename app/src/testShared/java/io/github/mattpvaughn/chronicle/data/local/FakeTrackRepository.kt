package io.github.mattpvaughn.chronicle.data.local

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import javax.inject.Inject

class FakeTrackRepository @Inject constructor() : ITrackRepository {

    val tracks = makeTracks()

    private fun makeTracks(): List<MediaItemTrack> {
        return FakeBookRepository.books.flatMap{ makeTracksForBook(it.id)  }
    }

    private fun makeTracksForBook(bookId: Int): List<MediaItemTrack> {
        return (0..5).map { makeTrack(bookId, it) }
    }

    private fun makeTrack(bookId: Int, trackId: Int): MediaItemTrack {
        return MediaItemTrack(id = trackId, parentServerId = bookId)
    }

    override suspend fun loadTracksForAudiobook(bookId: Int): Result<List<MediaItemTrack>> {
        return Result.success(tracks)
    }

    override suspend fun updateCachedStatus(trackId: Int, isCached: Boolean) {}

    override fun getAllTracks(): LiveData<List<MediaItemTrack>> {
        return MutableLiveData(tracks)
    }

    override suspend fun getAllTracksAsync(): List<MediaItemTrack> {
        return tracks
    }

    override fun getTracksForAudiobook(bookId: Int): LiveData<List<MediaItemTrack>> {
        return MutableLiveData<List<MediaItemTrack>>(tracks)
    }

    override suspend fun getTracksForAudiobookAsync(id: Int): List<MediaItemTrack> {
        return tracks
    }

    override suspend fun updateTrackProgress(trackProgress: Long, trackId: Int, lastViewedAt: Long) {}

    override suspend fun getTrackAsync(id: Int): MediaItemTrack? {
        require(tracks.isNotEmpty())
        return tracks[0]
    }

    override suspend fun getBookIdForTrack(trackId: Int): Int {
        return 0
    }

    override suspend fun clear() {}

    override suspend fun getCachedTracks(): List<MediaItemTrack> {
        return tracks
    }

    override suspend fun getTrackCountForBookAsync(bookId: Int): Int {
        return 0
    }

    override suspend fun getCachedTrackCountForBookAsync(bookId: Int): Int {
        return 0
    }

    override suspend fun uncacheAll() {}

    override suspend fun refreshData(): List<Result<Unit>> {
        TODO("Not yet implemented")
    }

    override suspend fun findTrackByTitle(title: String): MediaItemTrack? {
        TODO("Not yet implemented")
    }

}