package io.github.mattpvaughn.chronicle.features.player

import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.lifecycle.MutableLiveData

class FakeMediaServiceConnection(private val mediaControllerCompat: MediaControllerCompat) : IMediaServiceConnection{
    private val _isConnected = MutableLiveData<Boolean>(true)
    override val isConnected: MutableLiveData<Boolean>
        get() = _isConnected
    override val playbackState: MutableLiveData<PlaybackStateCompat>
        get() = MutableLiveData(EMPTY_PLAYBACK_STATE)
    override val nowPlaying: MutableLiveData<MediaMetadataCompat>
        get() = MutableLiveData(NOTHING_PLAYING )
    override val connectionCallbacks: MediaBrowserCompat.ConnectionCallback
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
    override val mediaControllerCallback: MediaControllerCompat.Callback
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
    override val mediaBrowser: MediaBrowserCompat
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
    override var mediaController: MediaControllerCompat?
        get() = mediaControllerCompat
        set(value) {}
    override val transportControls: MediaControllerCompat.TransportControls
        get() = mediaControllerCompat.transportControls

    override fun connect() {
        _isConnected.postValue(true)
    }

    override fun disconnect() {
        _isConnected.postValue(true)
    }

}