package io.github.mattpvaughn.chronicle.features.player

import android.app.Activity
import android.content.ComponentName
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.lifecycle.MutableLiveData

interface IMediaServiceConnection {
    val isConnected: MutableLiveData<Boolean>
    val playbackState: MutableLiveData<PlaybackStateCompat>
    val nowPlaying: MutableLiveData<MediaMetadataCompat>
    val connectionCallbacks: MediaBrowserCompat.ConnectionCallback
    val mediaControllerCallback: MediaControllerCompat.Callback
    val mediaBrowser: MediaBrowserCompat
    var mediaController: MediaControllerCompat?
    val transportControls: MediaControllerCompat.TransportControls?

    fun connect()
    fun disconnect()
}

class MediaServiceConnection(context: Activity, serviceComponent: ComponentName) :
    IMediaServiceConnection {
    override val isConnected = MutableLiveData<Boolean>(false)

    override val playbackState = MutableLiveData<PlaybackStateCompat>(EMPTY_PLAYBACK_STATE)
    override val nowPlaying = MutableLiveData<MediaMetadataCompat>(NOTHING_PLAYING)

    override val connectionCallbacks = object : MediaBrowserCompat.ConnectionCallback() {
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

                // Save the controller
                MediaControllerCompat.setMediaController(context, mediaController)
            }
        }

        override fun onConnectionSuspended() {
            // The Service has crashed. Disable transport controls until it automatically reconnects
            isConnected.postValue(false)
        }

        override fun onConnectionFailed() {
            // The Service has refused our connection
            isConnected.postValue(false)
        }
    }

    override val mediaControllerCallback = MediaControllerCallback()

    override val mediaBrowser: MediaBrowserCompat = MediaBrowserCompat(
        context,
        serviceComponent,
        connectionCallbacks,
        null
    )

    override var mediaController : MediaControllerCompat? = null

    override val transportControls: MediaControllerCompat.TransportControls?
        get() = mediaController?.transportControls


    inner class MediaControllerCallback : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            playbackState.postValue(state ?: EMPTY_PLAYBACK_STATE)
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            nowPlaying.postValue(metadata ?: NOTHING_PLAYING)
        }
    }

    override fun connect() {
        mediaBrowser.connect()
    }

    override fun disconnect() {
        mediaController?.unregisterCallback(mediaControllerCallback)
        mediaBrowser.disconnect()
    }

    companion object {
        // For Singleton instantiation.
        @Volatile
        var instance: MediaServiceConnection? = null

        fun getInstance(context: Activity, serviceComponent: ComponentName) =
            instance ?: synchronized(this) {
                instance ?: MediaServiceConnection(context, serviceComponent)
                    .also { instance = it }
            }
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
    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, 0)
    .build()
