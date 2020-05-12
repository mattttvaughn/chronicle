package io.github.mattpvaughn.chronicle.features.player

import android.content.ComponentName
import android.content.Context
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.lifecycle.MutableLiveData
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME
import javax.inject.Inject

class MediaServiceConnection @Inject constructor(
    context: Context,
    serviceComponent: ComponentName
) {
    val isConnected = MutableLiveData<Boolean>(false)
    val playbackState = MutableLiveData<PlaybackStateCompat>(EMPTY_PLAYBACK_STATE)
    val nowPlaying = MutableLiveData<MediaMetadataCompat>(NOTHING_PLAYING)

    private val connectionCallbacks = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            isConnected.postValue(true)

            // Get the token for the MediaSession
            mediaBrowser.sessionToken.also { token: MediaSessionCompat.Token ->

                // Create a MediaControllerCompat
                mediaController = MediaControllerCompat(
                    context,
                    token
                ).apply {
                    registerCallback(mediaControllerCallback)
                }
            }
        }

        override fun onConnectionSuspended() {
            // The Service has crashed. Disable transport controls until it automatically reconnects
            Log.i(APP_NAME, "Service connection suspended")
            isConnected.postValue(false)
        }

        override fun onConnectionFailed() {
            // The Service has refused our connection
            Log.i(APP_NAME, "Service connection failed")
            isConnected.postValue(false)
        }
    }

    val mediaControllerCallback = MediaControllerCallback()

    val mediaBrowser: MediaBrowserCompat = MediaBrowserCompat(
        context,
        serviceComponent,
        connectionCallbacks,
        null
    )

    lateinit var mediaController: MediaControllerCompat

    val transportControls: MediaControllerCompat.TransportControls by lazy {
        mediaController.transportControls
    }

    inner class MediaControllerCallback : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            playbackState.postValue(state ?: EMPTY_PLAYBACK_STATE)
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) =
            if (metadata?.id == null || metadata.title == null) {
                nowPlaying.postValue(NOTHING_PLAYING)
            } else {
                nowPlaying.postValue(metadata)
            }

        override fun onSessionDestroyed() {
            Log.i(APP_NAME, "Media controller callback is kill")
            isConnected.postValue(false)
            super.onSessionDestroyed()
        }
    }

    fun disconnect() {
        Log.i(APP_NAME, "Disconnecting MediaServiceConnection")
        isConnected.postValue(false)
        mediaController.unregisterCallback(mediaControllerCallback)
        mediaBrowser.disconnect()
    }

    fun connect() {
        mediaBrowser.connect()
    }

}

val EMPTY_PLAYBACK_STATE: PlaybackStateCompat = PlaybackStateCompat.Builder()
    .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE)
    .addCustomAction(SKIP_FORWARDS)
    .addCustomAction(SKIP_BACKWARDS)
    .addCustomAction(START_SLEEP_TIMER)
    .setState(PlaybackStateCompat.STATE_NONE, 0, 0f)
    .build()

val NOTHING_PLAYING: MediaMetadataCompat = MediaMetadataCompat.Builder()
    .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, "")
    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "")
    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, 0)
    .build()


