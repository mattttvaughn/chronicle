package io.github.mattpvaughn.chronicle.features.player

import android.app.Notification
import android.app.PendingIntent
import android.content.*
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.view.KeyEvent
import android.view.KeyEvent.*
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media.MediaBrowserServiceCompat
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.ext.cast.CastPlayer
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.CastStateListener
import io.github.mattpvaughn.chronicle.BuildConfig
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.application.ChronicleApplication
import io.github.mattpvaughn.chronicle.application.FEATURE_FLAG_IS_CAST_ENABLED
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.data.local.IBookRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository.Companion.TRACK_NOT_FOUND
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import io.github.mattpvaughn.chronicle.data.model.getActiveTrack
import io.github.mattpvaughn.chronicle.data.model.getProgress
import io.github.mattpvaughn.chronicle.data.model.toMediaItem
import io.github.mattpvaughn.chronicle.data.sources.plex.*
import io.github.mattpvaughn.chronicle.data.sources.plex.IPlexLoginRepo.LoginState.*
import io.github.mattpvaughn.chronicle.data.sources.plex.model.getDuration
import io.github.mattpvaughn.chronicle.features.player.SleepTimer.Companion.ARG_SLEEP_TIMER_ACTION
import io.github.mattpvaughn.chronicle.features.player.SleepTimer.Companion.ARG_SLEEP_TIMER_DURATION_MILLIS
import io.github.mattpvaughn.chronicle.features.player.SleepTimer.SleepTimerAction
import io.github.mattpvaughn.chronicle.injection.components.DaggerServiceComponent
import io.github.mattpvaughn.chronicle.injection.modules.ServiceModule
import io.github.mattpvaughn.chronicle.util.PackageValidator
import kotlinx.coroutines.*
import timber.log.Timber
import javax.inject.Inject

/** The service responsible for media playback, notification */
class MediaPlayerService : MediaBrowserServiceCompat(), ForegroundServiceController,
    SleepTimer.SleepTimerBroadcaster, CastStateListener {

    val serviceJob: CompletableJob = SupervisorJob()
    val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    @Inject
    lateinit var playbackErrorHandler: PlaybackErrorHandler

    @Inject
    lateinit var onMediaChangedCallback: OnMediaChangedCallback

    @Inject
    lateinit var packageValidator: PackageValidator

    @Inject
    lateinit var notificationBuilder: NotificationBuilder

    @Inject
    lateinit var plexConfig: PlexConfig

    @Inject
    lateinit var becomingNoisyReceiver: BecomingNoisyReceiver

    @Inject
    lateinit var mediaSession: MediaSessionCompat

    @Inject
    lateinit var mediaController: MediaControllerCompat

    @Inject
    lateinit var mediaSessionConnector: MediaSessionConnector

    @Inject
    lateinit var queueNavigator: QueueNavigator

    @Inject
    lateinit var exoPlayer: SimpleExoPlayer

    @Inject
    lateinit var castPlayer: CastPlayer

    @Inject
    lateinit var castContext: CastContext

    private val audioAttrs = AudioAttributes.Builder()
        .setContentType(C.CONTENT_TYPE_SPEECH) // pauses playback on temporary audio focus loss
        .setUsage(C.USAGE_MEDIA)
        .build()

    @Inject
    lateinit var bookRepository: IBookRepository

    @Inject
    lateinit var trackRepository: ITrackRepository

    @Inject
    lateinit var trackListManager: TrackListStateManager

    @Inject
    lateinit var mediaSessionCallback: AudiobookMediaSessionCallback

    @Inject
    lateinit var playbackPreparer: AudiobookPlaybackPreparer

    @Inject
    lateinit var prefsRepo: PrefsRepo

    @Inject
    lateinit var plexPrefs: PlexPrefsRepo

    @Inject
    lateinit var plexLoginRepo: IPlexLoginRepo

    companion object {
        /** Strings used by plex to indicate playback state */
        const val PLEX_STATE_PLAYING = "playing"
        const val PLEX_STATE_STOPPED = "stopped"
        const val PLEX_STATE_PAUSED = "paused"

        // Key indicating where in the audiobook to begin playback
        const val KEY_START_TIME_OFFSET = "track index bundle 2939829 tubers"

        // Value indicating to begin playback at the most recently listened position
        const val ACTIVE_TRACK = Long.MIN_VALUE + 22233L

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
    lateinit var localBroadcastManager: LocalBroadcastManager

    var currentPlayer: Player? = null

    @OptIn(InternalCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()

        DaggerServiceComponent.builder()
            .appComponent((application as ChronicleApplication).appComponent)
            .serviceModule(ServiceModule(this))
            .build()
            .inject(this)

        Timber.i("Service created!")

//        castContext.addCastStateListener(this)

        exoPlayer.setAudioAttributes(audioAttrs, true)

        prefsRepo.registerPrefsListener(prefsListener)

        serviceScope.launch(Injector.get().unhandledExceptionHandler()) { mediaSource.load() }

        mediaSession.setPlaybackState(EMPTY_PLAYBACK_STATE)
        mediaSession.setCallback(mediaSessionCallback)

        if (FEATURE_FLAG_IS_CAST_ENABLED) {
            currentPlayer = if (castPlayer.isCastSessionAvailable) castPlayer else exoPlayer
        }
        switchToPlayer(exoPlayer)
        if (FEATURE_FLAG_IS_CAST_ENABLED) {
            castPlayer.addListener(playerEventListener)
        }
        exoPlayer.addListener(playerEventListener)

        mediaSessionConnector.setCustomActionProviders(*makeCustomActionProviders(trackListManager))
        mediaSessionConnector.setQueueNavigator(queueNavigator)
        mediaSessionConnector.setPlaybackPreparer(playbackPreparer)
        mediaSessionConnector.setMediaButtonEventHandler { _, _, mediaButtonEvent ->
            mediaSessionCallback.onMediaButtonEvent(mediaButtonEvent)
        }

        mediaController.registerCallback(onMediaChangedCallback)

        // startForeground has to be called within 5 seconds of starting the service or the app
        // will ANR (on Android 9.0 and above, maybe earlier). Even if we don't have
        // full metadata here, should launch a notification with whatever it is we have...
        serviceScope.launch(Injector.get().unhandledExceptionHandler()) {
            val notification = withContext(Dispatchers.IO) {
                notificationBuilder.buildNotification(mediaSession.sessionToken)
            }
            startForeground(NOW_PLAYING_NOTIFICATION, notification)
        }

        localBroadcastManager.registerReceiver(
            sleepTimerBroadcastReceiver,
            IntentFilter(SleepTimer.ACTION_SLEEP_TIMER_CHANGE)
        )

        invalidatePlaybackParams()
        (progressUpdater as SimpleProgressUpdater).mediaSessionConnector = mediaSessionConnector
        progressUpdater.startRegularProgressUpdates()
    }

    override fun broadcastUpdate(sleepTimerAction: SleepTimerAction, durationMillis: Long) {
        val broadcastIntent = Intent(SleepTimer.ACTION_SLEEP_TIMER_CHANGE).apply {
            putExtra(ARG_SLEEP_TIMER_ACTION, sleepTimerAction)
            putExtra(ARG_SLEEP_TIMER_DURATION_MILLIS, durationMillis)
        }
        localBroadcastManager.sendBroadcast(broadcastIntent)
    }

    private val sleepTimerBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null) {
                val durationMillis = intent.getLongExtra(ARG_SLEEP_TIMER_DURATION_MILLIS, 0L)
                val action = intent.getSerializableExtra(ARG_SLEEP_TIMER_ACTION) as SleepTimerAction
                sleepTimer.handleAction(action, durationMillis)
            }
        }
    }


    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            PrefsRepo.KEY_SKIP_SILENCE, PrefsRepo.KEY_PLAYBACK_SPEED -> {
                invalidatePlaybackParams()
            }
        }
    }

    private fun invalidatePlaybackParams() {
        Timber.i("Playback params: speed = ${prefsRepo.playbackSpeed}, skip silence = ${prefsRepo.skipSilence}")
        currentPlayer?.setPlaybackParameters(
            PlaybackParameters(prefsRepo.playbackSpeed, 1.0f, prefsRepo.skipSilence)
        )
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        // Ensures that players will not block being removed as a foreground service
        exoPlayer.stop(true)
        if (FEATURE_FLAG_IS_CAST_ENABLED) {
            castPlayer.stop(true)
        }
    }

    override fun onDestroy() {
        Timber.i("Service destroyed")
        // Send one last update to local/remote servers that playback has stopped
        val trackId = mediaController.metadata.id
        if (trackId != null && trackId.toInt() != TRACK_NOT_FOUND) {
            progressUpdater.updateProgress(
                trackId.toInt(),
                PLEX_STATE_STOPPED,
                currentPlayer!!.currentPosition,
                true
            )
        }
        progressUpdater.cancel()
        serviceJob.cancel()

        prefsRepo.unregisterPrefsListener(prefsListener)
        localBroadcastManager.unregisterReceiver(sleepTimerBroadcastReceiver)
        sleepTimer.cancel()
        mediaSession.run {
            isActive = false
            release()
            val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
            intent.setPackage(packageName)
            intent.component = ComponentName(
                packageName,
                MediaPlayerService::class.qualifiedName
                    ?: "io.github.mattpvaughn.chronicle.features.player.MediaPlayerService"

            )
            intent.putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(ACTION_DOWN, 312202))
            // Allow the system to restart app past death on media button click. See onStartCommand
            setMediaButtonReceiver(
                PendingIntent.getService(
                    this@MediaPlayerService,
                    KEYCODE_MEDIA_PLAY,
                    intent,
                    0
                )
            )
        }
        mediaSession.setCallback(null)
        mediaController.unregisterCallback(onMediaChangedCallback)
        becomingNoisyReceiver.unregister()
        serviceJob.cancel()

        super.onDestroy()
    }

    /** Handle hardware commands from notifications and custom actions from UI as intents */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // No need to parse actions if none were provided
        Timber.i("Start command!")

        // Handle intents sent from notification clicks as media button events
        val ke: KeyEvent? = intent?.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
        Timber.i("Key event: $ke")
        if (ke != null) {
            mediaSessionCallback.onMediaButtonEvent(intent)
        }

        // startForeground has to be called within 5 seconds of starting the service or the app
        // will ANR (on Android 9.0+). Even if we don't have full metadata here for unknown reasons,
        // we should launch with whatever it is we have, assuming the event isn't the notification
        // itself being removed (KEYCODE_MEDIA_STOP)
        if (ke?.keyCode != KEYCODE_MEDIA_STOP) {
            serviceScope.launch(Injector.get().unhandledExceptionHandler()) {
                val notification = notificationBuilder.buildNotification(mediaSession.sessionToken)
                startForeground(NOW_PLAYING_NOTIFICATION, notification)
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        if (parentId == CHRONICLE_MEDIA_EMPTY_ROOT || !prefsRepo.allowAuto) {
            result.sendResult(mutableListOf())
            return
        }

        result.detach()
        serviceScope.launch(Injector.get().unhandledExceptionHandler()) {
            withContext(Dispatchers.IO) {
                when (parentId) {
                    CHRONICLE_MEDIA_ROOT_ID -> {
                        result.sendResult(
                            (listOf(
                                makeBrowsable(
                                    getString(R.string.auto_category_recently_listened),
                                    R.drawable.ic_recent
                                )
                            )
                                    + listOf(
                                makeBrowsable(
                                    getString(R.string.auto_category_offline),
                                    R.drawable.ic_cloud_download_white
                                )
                            )
                                    + listOf(
                                makeBrowsable(
                                    getString(R.string.auto_category_recently_added),
                                    R.drawable.ic_add
                                )
                            )
                                    + listOf(
                                makeBrowsable(
                                    getString(R.string.auto_category_library),
                                    R.drawable.nav_library
                                )
                            )
                                    ).toMutableList()
                        )
                    }
                    getString(R.string.auto_category_recently_listened) -> {
                        val recentlyListened = bookRepository.getRecentlyListenedAsync()
                        result.sendResult(recentlyListened.map { it.toMediaItem(plexConfig) }
                            .toMutableList())
                    }
                    getString(R.string.auto_category_recently_added) -> {
                        val recentlyAdded = bookRepository.getRecentlyAddedAsync()
                        result.sendResult(recentlyAdded.map { it.toMediaItem(plexConfig) }
                            .toMutableList())
                    }
                    getString(R.string.auto_category_library) -> {
                        val books = bookRepository.getAllBooksAsync()
                        result.sendResult(books.map { it.toMediaItem(plexConfig) }
                            .toMutableList())
                    }
                    getString(R.string.auto_category_offline) -> {
                        val offline = bookRepository.getCachedAudiobooksAsync()
                        result.sendResult(offline.map { it.toMediaItem(plexConfig) }
                            .toMutableList())
                    }
                }
            }
        }
    }

    override fun onSearch(
        query: String,
        extras: Bundle?,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        Timber.i("Searching! Query = $query")
        serviceScope.launch(Injector.get().unhandledExceptionHandler()) {
            val books = bookRepository.searchAsync(query)
            result.sendResult(books.map { it.toMediaItem(plexConfig) }.toMutableList())
        }
        result.detach()
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        Timber.i("Getting root!")

        val isClientLegal =
            packageValidator.isKnownCaller(clientPackageName, clientUid) || BuildConfig.DEBUG

        val extras = Bundle().apply {
            putBoolean(
                CHRONICLE_MEDIA_SEARCH_SUPPORTED,
                isClientLegal && prefsRepo.allowAuto && plexLoginRepo.loginState.value == LOGGED_IN_FULLY
            )
            putBoolean(CONTENT_STYLE_SUPPORTED, true)
            putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST_ITEM_HINT_VALUE)
            putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_LIST_ITEM_HINT_VALUE)
        }

        return when {
            !prefsRepo.allowAuto -> {
                mediaSessionConnector.setCustomErrorMessage(
                    getString(R.string.auto_access_error_auto_is_disabled)
                )
                BrowserRoot(CHRONICLE_MEDIA_EMPTY_ROOT, extras)
            }
            !isClientLegal -> {
                mediaSessionConnector.setCustomErrorMessage(
                    getString(R.string.auto_access_error_invalid_client)
                )
                BrowserRoot(CHRONICLE_MEDIA_EMPTY_ROOT, extras)
            }
            plexLoginRepo.loginState.value == NOT_LOGGED_IN -> {
                mediaSessionConnector.setCustomErrorMessage(
                    getString(R.string.auto_access_error_not_logged_in)
                )
                BrowserRoot(CHRONICLE_MEDIA_EMPTY_ROOT, extras)
            }
            plexLoginRepo.loginState.value == LOGGED_IN_NO_USER_CHOSEN -> {
                mediaSessionConnector.setCustomErrorMessage(
                    getString(R.string.auto_access_error_no_user_chosen)
                )
                BrowserRoot(CHRONICLE_MEDIA_EMPTY_ROOT, extras)
            }
            plexLoginRepo.loginState.value == LOGGED_IN_NO_SERVER_CHOSEN -> {
                mediaSessionConnector.setCustomErrorMessage(
                    getString(R.string.auto_access_error_no_server_chosen)
                )
                BrowserRoot(CHRONICLE_MEDIA_EMPTY_ROOT, extras)
            }
            plexLoginRepo.loginState.value == LOGGED_IN_NO_LIBRARY_CHOSEN -> {
                mediaSessionConnector.setCustomErrorMessage(
                    getString(R.string.auto_access_error_no_library_chosen)
                )
                BrowserRoot(CHRONICLE_MEDIA_EMPTY_ROOT, extras)
            }
            else -> {
                BrowserRoot(CHRONICLE_MEDIA_ROOT_ID, extras)
            }
        }
    }

    private val playerEventListener = object : Player.EventListener {

        override fun onPositionDiscontinuity(reason: Int) {
            super.onPositionDiscontinuity(reason)
            Timber.i("Player discontinuity!!!!!")
            serviceScope.launch(Injector.get().unhandledExceptionHandler()) {
                if (reason == Player.DISCONTINUITY_REASON_PERIOD_TRANSITION) {
                    // Update track progress
                    val trackId = mediaController.metadata.id
                    if (trackId != null && trackId != TRACK_NOT_FOUND.toString()) {
                        val plexState = PLEX_STATE_PLAYING
                        withContext(Dispatchers.IO) {
                            val bookId = trackRepository.getBookIdForTrack(trackId.toInt())
                            val track = trackRepository.getTrackAsync(trackId.toInt())
                            val tracks = trackRepository.getTracksForAudiobookAsync(bookId)
                            val isBookFinished =
                                tracks.getDuration() == tracks.getProgress()
                            if (isBookFinished) {
                                mediaController.transportControls.stop()
                            }
                            progressUpdater.updateProgress(
                                trackId.toInt(),
                                plexState,
                                track?.duration ?: 0L,
                                true
                            )
                        }
                    }
                }
            }
        }

        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            super.onPlayerStateChanged(playWhenReady, playbackState)
            if (playbackState != Player.STATE_ENDED) {
                return
            }
            Timber.i("Player STATE ENDED")
            serviceScope.launch(Injector.get().unhandledExceptionHandler()) {
                withContext(Dispatchers.IO) {
                    // get track through tracklistmanager b/c metadata will be empty
                    val activeTrack = trackListManager.trackList.getActiveTrack()
                    if (activeTrack.id != MediaItemTrack.EMPTY_TRACK.id) {
                        progressUpdater.updateProgress(
                            activeTrack.id,
                            PLEX_STATE_STOPPED,
                            activeTrack.duration,
                            true
                        )
                    }
                }
            }
        }
    }

    override fun onCastStateChanged(castState: Int) {
        Timber.i("Cast state: $castState")
        // TODO: finish adding casting
        if (FEATURE_FLAG_IS_CAST_ENABLED) {
            when (castState) {
                CastState.CONNECTED -> switchToPlayer(castPlayer)
                CastState.NOT_CONNECTED,
                CastState.NO_DEVICES_AVAILABLE,
                CastState.CONNECTING -> switchToPlayer(exoPlayer)
                else -> throw NoWhenBranchMatchedException("Unknown cast state: $castState")
            }
        }
    }

    private fun switchToPlayer(player: Player) {
        if (player == currentPlayer) {
            Timber.i("NOT SWITCHING PLAYER")
            return
        }
        Timber.i("SWITCHING PLAYER to $player")

        val prevPlayer: Player? = currentPlayer

        // If playback ended, reset player before we copy its state
        if (prevPlayer?.playbackState == Player.STATE_ENDED) {
            prevPlayer.stop(true)
        }

        mediaSessionConnector.setPlayer(player)
        mediaSessionCallback.currentPlayer = player

        prevPlayer?.let {
            player.seekTo(it.currentWindowIndex, it.currentPosition)
            player.playWhenReady = it.playWhenReady
        }

        currentPlayer = player

        // reset old player's state
        if (prevPlayer?.playbackState != Player.STATE_ENDED) {
            prevPlayer?.stop(true)
        }

        invalidatePlaybackParams()
    }


}

interface ServiceController {
    fun stopService()
}

interface ForegroundServiceController {
    fun startForeground(nowPlayingNotification: Int, notification: Notification)
    fun stopForeground(notificationActive: Boolean)
}
