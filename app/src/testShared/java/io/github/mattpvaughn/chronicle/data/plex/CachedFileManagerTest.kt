package io.github.mattpvaughn.chronicle.data.plex

import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack

class FakeCachedFileManager : ICachedFileManager{
    override fun cancelCaching() {
    }

    override fun downloadTracks(tracks: List<MediaItemTrack>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun uncacheAll(): Int {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun uncacheTracks(bookId: Int, tracks: List<MediaItemTrack>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}