package io.github.mattpvaughn.chronicle.features.player

import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.Player
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME
import javax.inject.Inject


class PlaybackErrorHandler @Inject constructor(private val broadcastManager: LocalBroadcastManager) :
    Player.EventListener {
    override fun onPlayerError(error: ExoPlaybackException) {
        Log.e(APP_NAME, "Exoplayer playback error: $error")
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
