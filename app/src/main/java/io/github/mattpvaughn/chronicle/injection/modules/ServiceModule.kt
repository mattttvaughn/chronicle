package io.github.mattpvaughn.chronicle.injection.modules

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.support.v4.media.RatingCompat.RATING_NONE
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.MediaSessionCompat.*
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import com.google.android.exoplayer2.util.Util
import dagger.Module
import dagger.Provides
import io.github.mattpvaughn.chronicle.BuildConfig
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.application.MainActivity
import io.github.mattpvaughn.chronicle.data.sources.plex.*
import io.github.mattpvaughn.chronicle.features.player.*
import io.github.mattpvaughn.chronicle.injection.scopes.ServiceScope
import io.github.mattpvaughn.chronicle.util.PackageValidator
import kotlinx.coroutines.CompletableJob

@Module
class ServiceModule(private val service: MediaPlayerService) {

    @Provides
    @ServiceScope
    fun service(): Service = service

    @Provides
    @ServiceScope
    fun serviceJob(): CompletableJob = service.serviceJob

    @Provides
    @ServiceScope
    fun serviceScope() = service.serviceScope

    @Provides
    @ServiceScope
    fun simpleExoPlayer(): SimpleExoPlayer = SimpleExoPlayer.Builder(service).build()

    @Provides
    @ServiceScope
    fun exoPlayer(simpleExoPlayer: SimpleExoPlayer): ExoPlayer = simpleExoPlayer

    @Provides
    @ServiceScope
    fun pendingIntent(): PendingIntent =
        service.packageManager.getLaunchIntentForPackage(service.packageName).let { sessionIntent ->
            sessionIntent?.putExtra(MainActivity.FLAG_OPEN_ACTIVITY_TO_CURRENTLY_PLAYING, true)
            PendingIntent.getActivity(service, 0, sessionIntent, 0)
        }

    @Provides
    @ServiceScope
    fun mediaSession(launchActivityPendingIntent: PendingIntent): MediaSessionCompat =
        MediaSessionCompat(service, APP_NAME).apply {
            // Enable callbacks from MediaButtons and TransportControls
            setFlags(FLAG_HANDLES_MEDIA_BUTTONS or FLAG_HANDLES_TRANSPORT_CONTROLS or FLAG_HANDLES_QUEUE_COMMANDS)
            service.sessionToken = sessionToken
            setSessionActivity(launchActivityPendingIntent)
            setRatingType(RATING_NONE)
            isActive = true
        }

    @Provides
    @ServiceScope
    fun localBroadcastManager() = LocalBroadcastManager.getInstance(service)

    @Provides
    @ServiceScope
    fun sleepTimerBroadcaster(): SleepTimer.SleepTimerBroadcaster = service

    @Provides
    @ServiceScope
    fun sleepTimer(simpleSleepTimer: SimpleSleepTimer): SleepTimer = simpleSleepTimer

    @Provides
    fun provideProgressUpdater(
        updater: SimpleProgressUpdater,
        mediaControllerCompat: MediaControllerCompat
    ): ProgressUpdater = updater.apply {
        mediaController = mediaControllerCompat
    }

    @Provides
    @ServiceScope
    fun provideCachedFileManager(cacheManager: CachedFileManager): ICachedFileManager = cacheManager

    @Provides
    @ServiceScope
    fun notificationManager(): NotificationManagerCompat = NotificationManagerCompat.from(service)

    @Provides
    @ServiceScope
    fun notificationBuilder(controller: MediaControllerCompat, plexConfig: PlexConfig) =
        NotificationBuilder(service, controller, plexConfig)

    @Provides
    @ServiceScope
    fun becomingNoisyReceiver(session: MediaSessionCompat) =
        BecomingNoisyReceiver(service, session.sessionToken)

    @Provides
    @ServiceScope
    fun mediaSessionConnector(session: MediaSessionCompat) = MediaSessionConnector(session)

    @Provides
    @ServiceScope
    fun serviceController() = object : ServiceController {
        override fun stopService() = service.stopSelf()
    }

    @Provides
    @ServiceScope
    fun plexDataSourceFactory(plexPrefs: PlexPrefsRepo): DefaultDataSourceFactory {
        val dataSourceFactory = DefaultHttpDataSourceFactory(Util.getUserAgent(service, APP_NAME))

        val props = dataSourceFactory.defaultRequestProperties
        props.set("X-Plex-Platform", "Android")
        props.set("X-Plex-Provides", "player")
        props.set("X-Plex_Client-Name", APP_NAME)
        props.set("X-Plex-Client-Identifier", plexPrefs.uuid) // TODO add a read UUID
        props.set("X-Plex-Version", BuildConfig.VERSION_NAME)
        props.set("X-Plex-Product", APP_NAME)
        props.set("X-Plex-Platform-Version", Build.VERSION.RELEASE)
        props.set("X-Plex-Device", Build.MODEL)
        props.set("X-Plex-Device-Name", Build.MODEL)
        props.set("X-Plex-Token", plexPrefs.user?.authToken ?: plexPrefs.accountAuthToken)

        return DefaultDataSourceFactory(service, dataSourceFactory)
    }

    @Provides
    @ServiceScope
    fun packageValidator() = PackageValidator(service, R.xml.auto_allowed_callers)

    @Provides
    @ServiceScope
    fun foregroundServiceController(): ForegroundServiceController = service

    @Provides
    @ServiceScope
    fun mediaController(session: MediaSessionCompat) =
        MediaControllerCompat(service, session.sessionToken)

    @Provides
    @ServiceScope
    fun mediaSessionCallback(callback: AudiobookMediaSessionCallback): Callback = callback

    @Provides
    @ServiceScope
    fun trackListManager(): TrackListStateManager = TrackListStateManager()

    @Provides
    @ServiceScope
    fun sensorManager(): SensorManager =
        service.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    @Provides
    @ServiceScope
    fun toneManager() = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
}


