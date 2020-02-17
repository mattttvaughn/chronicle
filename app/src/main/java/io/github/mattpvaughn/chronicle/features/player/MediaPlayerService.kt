package io.github.mattpvaughn.chronicle.features.player

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat.*
import android.util.Log
import android.view.KeyEvent
import android.view.KeyEvent.KEYCODE_MEDIA_STOP
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import androidx.work.WorkManager
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import com.google.android.exoplayer2.util.Util
import io.github.mattpvaughn.chronicle.BuildConfig
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.application.MainActivity.Companion.FLAG_OPEN_ACTIVITY_TO_CURRENTLY_PLAYING
import io.github.mattpvaughn.chronicle.data.local.*
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository.Companion.TRACK_NOT_FOUND
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import io.github.mattpvaughn.chronicle.data.model.toMediaItem
import io.github.mattpvaughn.chronicle.data.plex.*
import io.github.mattpvaughn.chronicle.features.settings.PrefsRepo
import io.github.mattpvaughn.chronicle.util.PackageValidator
import kotlinx.coroutines.*
import kotlin.math.abs

/**
 * The service responsible for media playback, notification
 */
open class MediaPlayerService : MediaBrowserServiceCompat(), ForegroundServiceController {

    // Validates packages which are allowed to accesss media, like Android Auto
    private lateinit var packageValidator: PackageValidator

    private lateinit var dataSourceFactory: DefaultDataSourceFactory
    // Receiver which handles intents regarding sudden headphone/bluetooth changes
    private lateinit var becomingNoisyReceiver: BecomingNoisyReceiver

    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var notificationBuilder: NotificationBuilder

    // Holds state of the active media session
    private lateinit var mediaSession: MediaSessionCompat

    private lateinit var mediaController: MediaControllerCompat
    private lateinit var mediaSessionConnector: MediaSessionConnector

    // Media player- actually turns media files/streams into sound from device
    private lateinit var exoPlayer: SimpleExoPlayer

    private val audioAttrs = AudioAttributes.Builder()
        .setContentType(C.CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    private lateinit var bookRepository: IBookRepository
    private lateinit var trackRepository: ITrackRepository

    private val serviceController = object : ServiceController {
        override fun startService() {
            ContextCompat.startForegroundService(
                applicationContext,
                Intent(applicationContext, this@MediaPlayerService.javaClass)
            )
        }

        override fun stopService() {
            stopSelf()
        }
    }

    private val trackListManager = TrackListStateManager()

    private var isForegroundService = false

    private lateinit var mediaSessionCallback: AudiobookMediaSessionCallback

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var prefsRepo: PrefsRepo

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

    private lateinit var sleepTimer: SleepTimer
    private lateinit var progressUpdater: ProgressUpdater

    @UseExperimental(InternalCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()

        exoPlayer = SimpleExoPlayer.Builder(this).build()
        exoPlayer.setAudioAttributes(audioAttrs, true)

        val localBroadcastManager = LocalBroadcastManager.getInstance(this)
        exoPlayer.addListener(PlaybackErrorHandler(localBroadcastManager))

        // Observe changes in media playback relevant preferences
        prefsRepo = Injector.get().prefsRepo()
        prefsRepo.registerPrefsListener(prefsListener)

        val plexPrefs = Injector.get().plexPrefs()

        bookRepository = Injector.get().bookRepo()
        trackRepository = Injector.get().trackRepo()
        Log.i(APP_NAME, "Offline mode? ${prefsRepo.offlineMode}")
        val authToken = plexPrefs.getAuthToken()

        val launchActivityPendingIntent =
            packageManager.getLaunchIntentForPackage(packageName).let { sessionIntent ->
                sessionIntent?.putExtra(FLAG_OPEN_ACTIVITY_TO_CURRENTLY_PLAYING, true)
                PendingIntent.getActivity(this, 0, sessionIntent, 0)
            }

        mediaSession = MediaSessionCompat(this, APP_NAME).apply {
            // Enable callbacks from MediaButtons and TransportControls
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                        or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            setSessionToken(sessionToken)
            setSessionActivity(launchActivityPendingIntent)
            setPlaybackState(EMPTY_PLAYBACK_STATE)

            isActive = true
        }
        mediaController = MediaControllerCompat(this, mediaSession.sessionToken)

        val workManager = WorkManager.getInstance(this)

        sleepTimer = SimpleSleepTimer(localBroadcastManager, mediaController)
        progressUpdater = SimpleProgressUpdater(
            serviceScope = serviceScope,
            trackRepository = trackRepository,
            bookRepository = bookRepository,
            workManager = workManager,
            inputMediaController = mediaController,
            prefsRepo = prefsRepo
        )

        notificationManager = NotificationManagerCompat.from(this)
        notificationBuilder = NotificationBuilder(this)

        becomingNoisyReceiver = BecomingNoisyReceiver(this, mediaSession.sessionToken)

        val mediaSource =
            PlexMediaRepository(
                bookRepository
            )
        serviceScope.launch { mediaSource.load() }

        mediaSessionConnector = MediaSessionConnector(mediaSession).also { mediaSessionConnector ->
            createPlexDataSourceFactory(authToken)

            mediaSessionCallback = AudiobookMediaSessionCallback(
                plexPrefsRepo = plexPrefs,
                prefsRepo = prefsRepo,
                mediaController = mediaController,
                exoPlayer = exoPlayer,
                dataSourceFactory = dataSourceFactory,
                trackRepository = trackRepository,
                bookRepository = bookRepository,
                sleepTimer = sleepTimer,
                serviceScope = serviceScope,
                trackListStateManager = trackListManager
            )

            val playbackPreparer = AudiobookPlaybackPreparer(mediaSource, mediaSessionCallback)

            mediaSessionConnector.setPlayer(exoPlayer)
            mediaSessionConnector.setPlaybackPreparer(playbackPreparer)
            mediaSessionConnector.setQueueNavigator(QueueNavigator(mediaSession))
            mediaSession.isActive = true
        }

        mediaController.registerCallback(
            MediaControllerCallback(
                mediaController = mediaController,
                serviceScope = serviceScope,
                trackRepository = trackRepository,
                progressUpdater = progressUpdater,
                notificationBuilder = notificationBuilder,
                mediaSession = mediaSession,
                becomingNoisyReceiver = becomingNoisyReceiver,
                notificationManager = notificationManager,
                foregroundServiceController = this@MediaPlayerService,
                serviceController = serviceController
            )
        )

        updatePlaybackParams()
        progressUpdater.startRegularProgressUpdates()

        packageValidator = PackageValidator(this, R.xml.auto_allowed_callers)
    }

    private fun createPlexDataSourceFactory(authToken: String) {
        val httpDataSourceFactory =
            DefaultHttpDataSourceFactory(Util.getUserAgent(this, APP_NAME))

        val props = httpDataSourceFactory.defaultRequestProperties
        props.set("X-Plex-Platform", "Android")
        props.set("X-Plex-Provides", "player")
        props.set("X-Plex_Client-Name", APP_NAME)
        props.set("X-Plex-Client-Identifier", "1111111") // TODO add a read UUID
        props.set("X-Plex-Version", BuildConfig.VERSION_NAME)
        props.set("X-Plex-Product", APP_NAME)
        props.set("X-Plex-Platform-Version", Build.VERSION.RELEASE)
        props.set("X-Plex-Device", Build.MODEL)
        props.set("X-Plex-Device-Name", Build.MODEL)
        props.set("X-Plex-Token", authToken)

        dataSourceFactory = DefaultDataSourceFactory(this, httpDataSourceFactory)
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
            PlaybackParameters(
                prefsRepo.playbackSpeed,
                1.0f,
                prefsRepo.skipSilence
            )
        )
    }

    class PlaybackErrorHandler(private val localBroadcastManager: LocalBroadcastManager) :
        Player.EventListener {
        override fun onPlayerError(error: ExoPlaybackException) {
            localBroadcastManager.sendBroadcast(makePlaybackErrorIntent(error))
            super.onPlayerError(error)
        }

        private fun makePlaybackErrorIntent(error: ExoPlaybackException): Intent {
            val intent = Intent(ACTION_PLAYBACK_ERROR)
            intent.putExtra(PLAYBACK_ERROR_MESSAGE, error.message)
            intent.putExtra(PLAYBACK_ERROR_TYPE, error.type)
            return intent
        }

        companion object {
            const val ACTION_PLAYBACK_ERROR = "playback error action intent"
            const val PLAYBACK_ERROR_MESSAGE = "playback error message"
            const val PLAYBACK_ERROR_TYPE = "playack error type"
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        // Ensures that exoplayer will not block the service from being removed as a foreground
        // service
        exoPlayer.stop()
    }

    override fun onDestroy() {
        Log.i(APP_NAME, "Service destroyed")
        // Stop updating local/remote servers about progress

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

        serviceJob.cancel()
        super.onDestroy()
    }

    /**
     * Shadows the state of tracks in the queue in order to calculate seeks for
     * [AudiobookMediaSessionCallback]
     */
    class TrackListStateManager {
        /** The list of [MediaItemTrack]s currently playing */
        var trackList = emptyList<MediaItemTrack>()

        /** The index of the current track within [trackList] */
        var currentTrackIndex: Int = 0

        /** The number of milliseconds between current position and start of track */
        var currentPosition: Long = 0

        /** Return the duration of a track at index [trackIndex] in milliseconds */
        private fun getTrackDuration(trackIndex: Int): Long {
            return trackList[trackIndex].duration
        }

        private fun currentTrackDuration(): Long {
            return getTrackDuration(currentTrackIndex)
        }

        fun hasNext(): Boolean {
            return currentTrackIndex < trackList.size
        }

        fun hasPrevious(): Boolean {
            return currentTrackIndex > 0
        }

        /** Seeks forwards or backwards in the playlist by [offset] millis*/
        fun seekByRelative(offset: Long) {
            if (offset > 0) {
                seekForwards(offset)
            } else {
                seekBackwards(abs(offset))
            }
        }

        /** Seek backwards by [offset] ms */
        private fun seekBackwards(offset: Long) {
            check(offset > 0)
            if (currentPosition > offset) {
                /** Skip backwards in current track by [offset] */
                currentPosition -= offset
            } else {
                /**
                 * Skip a combined [offset] milliseconds backwards between this track and previous
                 * one
                 */
                val amountSkippedFromFirstTrack = currentPosition
                currentPosition = if (hasPrevious()) {
                    currentTrackIndex--
                    currentTrackDuration() - (offset - amountSkippedFromFirstTrack)
                } else {
                    0
                }
            }
        }

        private fun seekForwards(offset: Long) {
            if (currentPosition + offset <= currentTrackDuration()) {
                // Skip 30s forward in current track
                currentPosition += offset
            } else {
                // Skip a combined 30s forward b/w this track and next one
                val amountSkippedFromFirstTrack = currentTrackDuration() - currentPosition
                currentPosition = if (hasNext()) {
                    currentTrackIndex++
                    offset - amountSkippedFromFirstTrack
                } else {
                    currentTrackDuration()
                }
            }
        }
    }

    class QueueNavigator(mediaSession: MediaSessionCompat) :
        TimelineQueueNavigator(mediaSession) {
        private val window = Timeline.Window()
        override fun getMediaDescription(
            player: Player,
            windowIndex: Int
        ): MediaDescriptionCompat =
            player.currentTimeline.getWindow(
                windowIndex,
                window
            ).tag as MediaDescriptionCompat
    }

    /** Handle hardware commands from notifications and custom actions from UI */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        /**
         * TODO: MediaButtonReceiver does not handle custom actions by default, and
         *       MediaButtonReceiver.handleIntent() fails to forward custom actions to the callback,
         *       so instead handle custom actions here by passing them to the callback directly.
         *       Figure out why MediaButtonReceiver isn't working- probably something to do with the
         *       custom actions registered in the [PlaybackState]
         */
        mediaSession.setCallback(mediaSessionCallback)
        val keyEvent = intent?.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
        if (keyEvent != null) {
            Log.i(APP_NAME, "Event keycode is ${keyEvent.keyCode}")
            when (keyEvent.keyCode) {
                mediaSkipForwardCode -> {
                    mediaController.transportControls.sendCustomAction(SKIP_FORWARDS, null)
                }
                mediaSkipBackwardCode -> {
                    mediaController.transportControls.sendCustomAction(SKIP_BACKWARDS, null)
                }
                toKeyCode(ACTION_PLAY) -> {
                    mediaController.transportControls.play()
                }
                toKeyCode(ACTION_PAUSE) -> {
                    mediaController.transportControls.pause()
                }
                KEYCODE_MEDIA_STOP -> {
                    stopSelf()
                }
                else -> throw NoWhenBranchMatchedException("Unknown custom action: $keyEvent")
            }
        }
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        if (parentId == CHRONICLE_MEDIA_EMPTY_ROOT) {
            result.sendResult(null)
            return
        }

        Log.i(APP_NAME, "Loading children")

        serviceScope.launch {
            // Top level load: load books into [result]
            if (parentId == CHRONICLE_MEDIA_ROOT_ID) {
                val books = bookRepository.getAllBooksAsync()
                result.sendResult(books.map { it.toMediaItem() }.toMutableList())
            }
        }
        result.detach()
    }

    override fun onSearch(
        query: String,
        extras: Bundle?,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        serviceScope.launch {
            val books = bookRepository.searchAsync(query)
            Log.d(APP_NAME, "Books: $books")
            result.sendResult(books.map { it.toMediaItem() }.toMutableList())
        }
        result.detach()
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        val isClientLegal =
            packageValidator.isKnownCaller(clientPackageName, clientUid) || BuildConfig.DEBUG

        val extras = Bundle().apply { putBoolean(CHRONICLE_MEDIA_SEARCH_SUPPORTED, isClientLegal) }
        Log.i(APP_NAME, "Client allowed? $isClientLegal")
        return if (isClientLegal) {
            BrowserRoot(CHRONICLE_MEDIA_ROOT_ID, extras)
        } else {
            BrowserRoot(CHRONICLE_MEDIA_EMPTY_ROOT, extras)
        }
    }

    override fun stopForeground() {
        this@MediaPlayerService.stopForeground()
    }

    override fun startForeground() {
        this@MediaPlayerService.startForeground()
    }

    override fun setForegroundServiceActive(isForeground: Boolean) {
        isForegroundService = isForeground
    }

    override fun isForegroundServiceActive(): Boolean {
        return isForegroundService
    }
}

interface ServiceController {
    fun startService()
    fun stopService()
}

interface ForegroundServiceController {
    fun stopForeground()
    fun startForeground()
    fun setForegroundServiceActive(isForeground: Boolean)
    fun isForegroundServiceActive(): Boolean
    fun startForeground(nowPlayingNotification: Int, notification: Notification)
    fun stopForeground(b: Boolean)
}
