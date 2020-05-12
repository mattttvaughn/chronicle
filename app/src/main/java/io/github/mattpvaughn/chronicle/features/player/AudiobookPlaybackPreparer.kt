package io.github.mattpvaughn.chronicle.features.player

import android.net.Uri
import android.os.Bundle
import android.os.ResultReceiver
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.google.android.exoplayer2.ControlDispatcher
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import io.github.mattpvaughn.chronicle.data.model.toMediaMetadata
import io.github.mattpvaughn.chronicle.data.plex.PlexMediaRepository
import java.io.File
import javax.inject.Inject

class AudiobookPlaybackPreparer @Inject constructor(
    private val mediaSource: PlexMediaRepository,
    private val mediaSessionCallback: MediaSessionCompat.Callback
) : MediaSessionConnector.PlaybackPreparer {

    override fun onPrepareFromSearch(query: String, playWhenReady: Boolean, extras: Bundle) {}

    override fun onCommand(
        player: Player,
        controlDispatcher: ControlDispatcher,
        command: String,
        extras: Bundle,
        cb: ResultReceiver
    ): Boolean {
        return false
    }

    override fun getSupportedPrepareActions(): Long =
        PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID or PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID

    override fun onPrepareFromMediaId(bookId: String, playWhenReady: Boolean, extras: Bundle) {
        mediaSource.whenReady {
            if (playWhenReady) {
                mediaSessionCallback.onPlayFromMediaId(bookId, extras)
            } else {
                mediaSessionCallback.onPrepareFromMediaId(bookId, extras)
            }
        }
    }

    override fun onPrepareFromUri(uri: Uri, playWhenReady: Boolean, extras: Bundle) = Unit

    override fun onPrepare(playWhenReady: Boolean) = Unit

}

fun buildPlaylist(tracks: List<MediaItemTrack>, file: File): List<MediaMetadataCompat> {
    return tracks.map { track -> track.toMediaMetadata() }
}
