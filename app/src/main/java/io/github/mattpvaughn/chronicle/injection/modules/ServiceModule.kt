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
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.util.Util
import dagger.Module
import dagger.Provides
import io.github.mattpvaughn.chronicle.BuildConfig
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.application.MainActivity
import io.github.mattpvaughn.chronicle.data.sources.plex.APP_NAME
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexPrefsRepo
import io.github.mattpvaughn.chronicle.features.player.*
import io.github.mattpvaughn.chronicle.features.player.MediaPlayerService.Companion.EXOPLAYER_BACK_BUFFER_DURATION_MILLIS
import io.github.mattpvaughn.chronicle.features.player.MediaPlayerService.Companion.EXOPLAYER_MAX_BUFFER_DURATION_MILLIS
import io.github.mattpvaughn.chronicle.features.player.MediaPlayerService.Companion.EXOPLAYER_MIN_BUFFER_DURATION_MILLIS
import io.github.mattpvaughn.chronicle.injection.scopes.ServiceScope
import io.github.mattpvaughn.chronicle.util.PackageValidator
import kotlinx.coroutines.CompletableJob
import kotlin.time.ExperimentalTime

@ExperimentalTime
@Module
class ServiceModule(private val service: MediaPlayerService) {

    @Provides
    @ServiceScope
    fun service(): Service = service

    @Provides
    @ServiceScope
    fun serviceController(): ServiceController = service

    @Provides
    @ServiceScope
    fun serviceJob(): CompletableJob = service.serviceJob

    @Provides
    @ServiceScope
    fun serviceScope() = service.serviceScope

    @Provides
    @ServiceScope
    fun exoPlayer(): ExoPlayer = ExoPlayer.Builder(service).setLoadControl(
        // increase buffer size across the board as ExoPlayer defaults are set for video
        DefaultLoadControl.Builder().setBackBuffer(EXOPLAYER_BACK_BUFFER_DURATION_MILLIS, true)
            .setBufferDurationsMs(
                EXOPLAYER_MIN_BUFFER_DURATION_MILLIS,
                EXOPLAYER_MAX_BUFFER_DURATION_MILLIS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .createDefaultLoadControl()
    ).build()

//    @Provides
//    @ServiceScope
//    fun exoPlayer(exoPlayer: ExoPlayer): ExoPlayer = exoPlayer

    @Provides
    @ServiceScope
    fun pendingIntent(): PendingIntent =
        service.packageManager.getLaunchIntentForPackage(service.packageName).let { sessionIntent ->
            sessionIntent?.putExtra(MainActivity.FLAG_OPEN_ACTIVITY_TO_CURRENTLY_PLAYING, true)
            PendingIntent.getActivity(
                service,
                MainActivity.REQUEST_CODE_OPEN_APP_TO_CURRENTLY_PLAYING,
                sessionIntent,
                PendingIntent.FLAG_IMMUTABLE
            )
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
    fun notificationManager(): NotificationManagerCompat = NotificationManagerCompat.from(service)

    @Provides
    @ServiceScope
    fun becomingNoisyReceiver(session: MediaSessionCompat) =
        BecomingNoisyReceiver(service, session.sessionToken)

    @Provides
    @ServiceScope
    fun mediaSessionConnector(session: MediaSessionCompat) = MediaSessionConnector(session)

    @Provides
    @ServiceScope
    fun plexDataSourceFactory(plexPrefs: PlexPrefsRepo): DefaultHttpDataSource.Factory {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
        dataSourceFactory.setUserAgent(Util.getUserAgent(service, APP_NAME))

        dataSourceFactory.setDefaultRequestProperties(
            mapOf(
                "X-Plex-Platform" to "Android",
                "X-Plex-Provides" to "player",
                "X-Plex_Client-Name" to APP_NAME,
                "X-Plex-Client-Identifier" to plexPrefs.uuid,
                "X-Plex-Version" to BuildConfig.VERSION_NAME,
                "X-Plex-Product" to APP_NAME,
                "X-Plex-Platform-Version" to Build.VERSION.RELEASE,
                "X-Plex-Device" to Build.MODEL,
                "X-Plex-Device-Name" to Build.MODEL,
                "X-Plex-Token" to (
                    plexPrefs.server?.accessToken ?: plexPrefs.user?.authToken
                        ?: plexPrefs.accountAuthToken
                    )
            )
        )

        return dataSourceFactory
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
