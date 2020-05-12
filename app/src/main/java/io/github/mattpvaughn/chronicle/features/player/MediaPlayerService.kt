package io.github.mattpvaughn.chronicle.features.player

import android.app.Notification
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import android.view.KeyEvent.KEYCODE_MEDIA_STOP
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import io.github.mattpvaughn.chronicle.BuildConfig
import io.github.mattpvaughn.chronicle.application.ChronicleApplication
import io.github.mattpvaughn.chronicle.data.local.IBookRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository.Companion.TRACK_NOT_FOUND
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME
import io.github.mattpvaughn.chronicle.data.plex.PlexMediaRepository
import io.github.mattpvaughn.chronicle.data.plex.PlexPrefsRepo
import io.github.mattpvaughn.chronicle.features.player.AudiobookMediaSessionCallback.TrackListStateManager
import io.github.mattpvaughn.chronicle.injection.components.DaggerServiceComponent
import io.github.mattpvaughn.chronicle.injection.modules.ServiceModule
import io.github.mattpvaughn.chronicle.util.PackageValidator
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The service responsible for media playback, notification
 */
open class MediaPlayerService : MediaBrowserServiceCompat(), ForegroundServiceController {
    @Inject
    lateinit var playbackErrorHandler: PlaybackErrorHandler

    @Inject
    lateinit var mediaControllerCallback: MediaControllerCallback

    // Validates packages which are allowed to accesss media, like Android Auto
    @Inject
    lateinit var packageValidator: PackageValidator

    // Receiver which handles intents regarding sudden headphone/bluetooth changes
    @Inject
    lateinit var becomingNoisyReceiver: BecomingNoisyReceiver

    // Holds state of the active media session
    @Inject
    lateinit var mediaSession: MediaSessionCompat

    @Inject
    lateinit var mediaController: MediaControllerCompat

    @Inject
    lateinit var mediaSessionConnector: MediaSessionConnector

    // Media player- actually turns media files/streams into sound from device
    @Inject
    lateinit var exoPlayer: SimpleExoPlayer

    private val audioAttrs = AudioAttributes.Builder()
        .setContentType(C.CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    @Inject
    lateinit var bookRepository: IBookRepository

    @Inject
    lateinit var trackRepository: ITrackRepository

    @Inject
    lateinit var trackListManager: TrackListStateManager

    private var isForegroundService = false

    @Inject
    lateinit var mediaSessionCallback: AudiobookMediaSessionCallback

    @Inject
    lateinit var serviceJob: CompletableJob

    @Inject
    lateinit var serviceScope: CoroutineScope

    @Inject
    lateinit var prefsRepo: PrefsRepo

    @Inject
    lateinit var plexPrefs: PlexPrefsRepo

    companion object {
        /** Strings used by plex to indicate playback state */
        const val PLEX_STATE_PLAYING = "playing"
        const val PLEX_STATE_STOPPED = "stopped"
        const val PLEX_STATE_PAUSED = "paused"

        const val KEY_PLAY_STARTING_WITH_TRACK_ID = "track index bundle 2939829 tubers"
        const val ACTIVE_TRACK = -123

        private const val CHRONICLE_MEDIA_ROOT_ID = "chronicle_media_root_id"
        private const val CHRONICLE_MEDIA_EMPTY_ROOT = "empty root"
        private const val CHRONICLE_MEDIA_SEARCH_SUPPORTED = "android.media.browse.SEARCH_SUPPORTED"
    }

    @Inject
    lateinit var sleepTimer: SleepTimer

    @Inject
    lateinit var progressUpdater: ProgressUpdater

    @Inject
    lateinit var mediaSource: PlexMediaRepository

    @Inject
    lateinit var queueNavigator: QueueNavigator

    @UseExperimental(InternalCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()

        DaggerServiceComponent.builder()
            .appComponent((application as ChronicleApplication).appComponent)
            .serviceModule(ServiceModule(this))
            .build()
            .inject(this)

        Log.i(APP_NAME, "Service created!")

        exoPlayer.setAudioAttributes(audioAttrs, true)
        exoPlayer.addListener(playbackErrorHandler)

        prefsRepo.registerPrefsListener(prefsListener)

        serviceScope.launch { mediaSource.load() }

        mediaSessionConnector.apply {
            val playbackPreparer = AudiobookPlaybackPreparer(mediaSource, mediaSessionCallback)
            setPlayer(exoPlayer)
            setPlaybackPreparer(playbackPreparer)
            setQueueNavigator(queueNavigator)
            setCustomActionProviders(*makeCustomActionProviders(sleepTimer, trackListManager))
            mediaSession.isActive = true
        }

        mediaController.registerCallback(mediaControllerCallback)

        updatePlaybackParams()
        progressUpdater.startRegularProgressUpdates()
    }


    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            PrefsRepo.KEY_SKIP_SILENCE, PrefsRepo.KEY_PLAYBACK_SPEED -> {
                Log.i(APP_NAME, "Change in media prefs!")
                updatePlaybackParams()
            }
        }
    }

    private fun updatePlaybackParams() {
        exoPlayer.setPlaybackParameters(
            PlaybackParameters(prefsRepo.playbackSpeed, 1.0f, prefsRepo.skipSilence)
        )
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        // Ensures that exoplayer will not block the service from being removed as a foreground
        // service
        exoPlayer.stop()
    }

    override fun onDestroy() {
        Log.i(APP_NAME, "Service destroyed")
        // Send one last update to local/remote servers that playback has stopped
        val trackId = mediaController.metadata.id
        if (trackId != null && trackId != TRACK_NOT_FOUND.toString()) {
            progressUpdater.updateProgress(
                mediaController.metadata.id?.toInt() ?: TRACK_NOT_FOUND,
                PLEX_STATE_STOPPED,
                exoPlayer.currentPosition
            )
        }
        progressUpdater.cancel()

        // Unregister sharedPrefs listener
        prefsRepo.unRegisterPrefsListener(prefsListener)
        sleepTimer.cancel()

        mediaSession.run {
            isActive = false
            release()
        }
        mediaSession.setCallback(null)
        mediaController.unregisterCallback(mediaControllerCallback)
        becomingNoisyReceiver.unregister()
        serviceJob.cancel()

        super.onDestroy()
    }

    /** Handle hardware commands from notifications and custom actions from UI */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        /**
         * MediaButtonReceiver does not handle custom actions, so instead handle custom actions here
         * by passing them to the callback directly.
         */
        val keyEvent = MediaButtonReceiver.handleIntent(mediaSession, intent)
        when (keyEvent.keyCode) {
            mediaSkipForwardCode -> mediaController.transportControls.sendCustomAction(
                SKIP_FORWARDS,
                null
            )
            mediaSkipBackwardCode -> mediaController.transportControls.sendCustomAction(
                SKIP_BACKWARDS,
                null
            )
            KEYCODE_MEDIA_STOP -> stopSelf()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
//        if (parentId == CHRONICLE_MEDIA_EMPTY_ROOT) {
//            result.sendResult(null)
//            return
//        }
//
//        serviceScope.launch {
//            // Top level load: load books into [result]
//            if (parentId == CHRONICLE_MEDIA_ROOT_ID) {
//                val books = bookRepository.getAllBooksAsync()
//                result.sendResult(books.map { it.toMediaItem() }.toMutableList())
//            }
//        }
//        result.detach()
        result.sendResult(null)
    }

    override fun onSearch(
        query: String,
        extras: Bundle?,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
//        serviceScope.launch {
//            val books = bookRepository.searchAsync(query)
//        }
//        result.detach()
        result.sendResult(null)
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        val isClientLegal =
            packageValidator.isKnownCaller(clientPackageName, clientUid) || BuildConfig.DEBUG

        val xtras = Bundle().apply { putBoolean(CHRONICLE_MEDIA_SEARCH_SUPPORTED, isClientLegal) }
//        Log.i(APP_NAME, "Client allowed? $isClientLegal")
//        return if (isClientLegal) {
//            BrowserRoot(CHRONICLE_MEDIA_ROOT_ID, xtras)
//        } else {
//        BrowserRoot(CHRONICLE_MEDIA_EMPTY_ROOT, null)
//        }

        return BrowserRoot(CHRONICLE_MEDIA_EMPTY_ROOT, null)
    }

    override fun setForegroundServiceActive(isForeground: Boolean) {
        isForegroundService = isForeground
    }

    override fun isForegroundServiceActive(): Boolean {
        return isForegroundService
    }
}

interface ServiceController {
    fun stopService()
}

interface ForegroundServiceController {
    fun setForegroundServiceActive(isForeground: Boolean)
    fun isForegroundServiceActive(): Boolean
    fun startForeground(nowPlayingNotification: Int, notification: Notification)
    fun stopForeground(notificationActive: Boolean)
}
