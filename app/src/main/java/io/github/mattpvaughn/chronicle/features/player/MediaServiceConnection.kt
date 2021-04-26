package io.github.mattpvaughn.chronicle.features.player

import android.content.ComponentName
import android.content.Context
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.session.PlaybackStateCompat.Builder
import android.support.v4.media.session.PlaybackStateCompat.STATE_NONE
import androidx.lifecycle.MutableLiveData
import io.github.mattpvaughn.chronicle.injection.scopes.ActivityScope
import timber.log.Timber
import javax.inject.Inject

@ActivityScope
class MediaServiceConnection @Inject constructor(
    applicationContext: Context,
    serviceComponent: ComponentName
) {
    val isConnected = MutableLiveData(false)
    val playbackState = MutableLiveData(EMPTY_PLAYBACK_STATE)
    val nowPlaying = MutableLiveData(NOTHING_PLAYING)

    private val connectionCallbacks = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            isConnected.postValue(true)

            // Create a MediaControllerCompat from the session token
            mediaController = MediaControllerCompat(
                applicationContext,
                mediaBrowser.sessionToken
            ).apply {
                registerCallback(mediaControllerCallback)
                this@MediaServiceConnection.transportControls = transportControls
            }

            // If the service already exists, bind the state right now
            if (mediaController?.playbackState?.state ?: STATE_NONE != STATE_NONE) {
                playbackState.postValue(mediaController?.playbackState ?: EMPTY_PLAYBACK_STATE)
                nowPlaying.value = mediaController?.metadata ?: NOTHING_PLAYING
            }
        }

        override fun onConnectionSuspended() {
            // The Service has crashed. Disable transport controls until it automatically reconnects
            Timber.i("Service connection suspended")
            isConnected.postValue(false)
        }

        override fun onConnectionFailed() {
            // The Service has refused our connection
            Timber.i("Service connection failed")
            isConnected.postValue(false)
        }
    }

    val mediaControllerCallback = MediaControllerCallback()

    val mediaBrowser: MediaBrowserCompat = MediaBrowserCompat(
        applicationContext,
        serviceComponent,
        connectionCallbacks,
        null
    )

    var mediaController: MediaControllerCompat? = null

    var transportControls: MediaControllerCompat.TransportControls? = null

    inner class MediaControllerCallback : MediaControllerCompat.Callback() {
        // Dangerous- easy to leak this lambda as [MediaServiceConnection] is application-scoped
        var onConnected: () -> Unit? = {}

        override fun onSessionReady() {
            Timber.i("MediaController session ready")
            onConnected.invoke()
            super.onSessionReady()
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            onConnected = {}
            Timber.i("MediaController state: $state")
            playbackState.postValue(state ?: EMPTY_PLAYBACK_STATE)
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            onConnected = {}
            Timber.i("MediaController metadata: ${metadata?.describe()}")
            if (metadata?.id == null || metadata.title == null) {
                nowPlaying.value = NOTHING_PLAYING
            } else {
                nowPlaying.value = metadata
            }
        }

        override fun onSessionDestroyed() {
            onConnected = {}
            Timber.i("MediaController callback is kill")
            isConnected.postValue(false)
            super.onSessionDestroyed()
        }
    }

    fun disconnect() {
        Timber.i("Disconnecting MediaServiceConnection")
        isConnected.postValue(false)
        mediaControllerCallback.onConnected = {}
        mediaController?.unregisterCallback(mediaControllerCallback)
        mediaBrowser.disconnect()
    }

    fun connect() {
        mediaBrowser.connect()
    }

    fun connect(onConnected: () -> Unit?) {
        mediaBrowser.connect()
        mediaControllerCallback.onConnected = onConnected
    }

}

val EMPTY_PLAYBACK_STATE: PlaybackStateCompat = Builder()
    .setState(STATE_NONE, 0, 0f)
    .build()

val NOTHING_PLAYING: MediaMetadataCompat = MediaMetadataCompat.Builder()
    .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, "")
    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "")
    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, 0)
    .build()


