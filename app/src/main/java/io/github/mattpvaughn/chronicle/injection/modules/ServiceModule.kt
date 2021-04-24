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
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import com.google.android.exoplayer2.util.Util
import dagger.Module
import dagger.Provides
import io.github.mattpvaughn.chronicle.BuildConfig
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.application.MainActivity
import io.github.mattpvaughn.chronicle.data.APP_NAME
import io.github.mattpvaughn.chronicle.data.CachedFileManager
import io.github.mattpvaughn.chronicle.data.ICachedFileManager
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
    fun simpleExoPlayer(): SimpleExoPlayer = SimpleExoPlayer.Builder(service).setLoadControl(
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

    @Provides
    @ServiceScope
    fun exoPlayer(simpleExoPlayer: SimpleExoPlayer): ExoPlayer = simpleExoPlayer

    @Provides
    @ServiceScope
    fun pendingIntent(): PendingIntent =
        service.packageManager.getLaunchIntentForPackage(service.packageName).let { sessionIntent ->
            sessionIntent?.putExtra(MainActivity.FLAG_OPEN_ACTIVITY_TO_CURRENTLY_PLAYING, true)
            PendingIntent.getActivity(
                service,
                MainActivity.REQUEST_CODE_OPEN_APP_TO_CURRENTLY_PLAYING,
                sessionIntent,
                0
            )
        }

    @Provides
    @ServiceScope
    fun mediaSession(launchActivityPendingIntent: PendingIntent): MediaSessionCompat =
        MediaSessionCompat(
            service,
            APP_NAME
        ).apply {
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

    @Provides
    @ServiceScope
    fun sourceController(): SourceController = service
}


