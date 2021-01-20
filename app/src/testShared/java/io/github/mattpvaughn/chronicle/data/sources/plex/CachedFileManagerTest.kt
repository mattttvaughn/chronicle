package io.github.mattpvaughn.chronicle.data.sources.plex

import io.github.mattpvaughn.chronicle.data.ICachedFileManager
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack

class FakeCachedFileManager :
    ICachedFileManager {
    override fun cancelCaching() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun downloadTracks(tracks: List<MediaItemTrack>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override suspend fun uncacheAllInLibrary(): Int {
        TODO("Not yet implemented")
    }

    override suspend fun deleteCachedBook(tracks: List<MediaItemTrack>): Result<Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun hasUserCachedTracks(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override suspend fun refreshTrackDownloadedStatus() {
        TODO("Not yet implemented")
    }

    override suspend fun handleDownloadedTrack(downloadId: Long): Result<Long> {
        TODO("Not yet implemented")
    }

}