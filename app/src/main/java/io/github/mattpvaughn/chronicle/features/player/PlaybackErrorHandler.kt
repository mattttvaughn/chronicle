package io.github.mattpvaughn.chronicle.features.player

import android.content.Intent
import android.support.v4.media.session.PlaybackStateCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import timber.log.Timber
import javax.inject.Inject

/** Responsible for handling errors in [Player]s and sending error messages to the user */
class PlaybackErrorHandler @Inject constructor(
    private val broadcastManager: LocalBroadcastManager,
    private val session: MediaSessionConnector
) : Player.EventListener {
    override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
        if (playbackState != PlaybackStateCompat.STATE_ERROR) {
            // clear errors if playback is proceeding correctly
            session.setCustomErrorMessage(null)
        }
        super.onPlayerStateChanged(playWhenReady, playbackState)
    }

    override fun onPlayerError(error: ExoPlaybackException) {
        Timber.e("Exoplayer playback error: $error")
        val errorIntent = Intent(ACTION_PLAYBACK_ERROR)
        errorIntent.putExtra(PLAYBACK_ERROR_MESSAGE, error.message)
        broadcastManager.sendBroadcast(errorIntent)
        super.onPlayerError(error)
    }

    companion object {
        const val ACTION_PLAYBACK_ERROR = "playback error action intent"
        const val PLAYBACK_ERROR_MESSAGE = "playback error message"
    }
}
