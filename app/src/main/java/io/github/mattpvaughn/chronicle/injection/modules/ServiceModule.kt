package io.github.mattpvaughn.chronicle.injection.modules

import android.app.PendingIntent
import android.os.Build
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
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
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME
import io.github.mattpvaughn.chronicle.data.plex.CachedFileManager
import io.github.mattpvaughn.chronicle.data.plex.ICachedFileManager
import io.github.mattpvaughn.chronicle.data.plex.PlexPrefsRepo
import io.github.mattpvaughn.chronicle.features.player.*
import io.github.mattpvaughn.chronicle.injection.scopes.ServiceScope
import io.github.mattpvaughn.chronicle.util.PackageValidator
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
class ServiceModule(private val service: MediaPlayerService) {

    @Provides
    @ServiceScope
    fun serviceJob(): CompletableJob = SupervisorJob()

    @Provides
    @ServiceScope
    fun serviceScope(serviceJob: CompletableJob) = CoroutineScope(Dispatchers.Main + serviceJob)

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
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            service.sessionToken = sessionToken
            setSessionActivity(launchActivityPendingIntent)
            setPlaybackState(EMPTY_PLAYBACK_STATE)
            isActive = true
        }

    @Provides
    @ServiceScope
    fun localBroadcastManager() = LocalBroadcastManager.getInstance(service)

    @Provides
    @ServiceScope
    fun sleepTimer(simpleSleepTimer: SimpleSleepTimer): SleepTimer = simpleSleepTimer

    @Provides
    fun provideProgressUpdater(updater: SimpleProgressUpdater): ProgressUpdater = updater

    @Provides
    fun provideCachedFileManager(cacheManager: CachedFileManager): ICachedFileManager = cacheManager

    @Provides
    @ServiceScope
    fun notificationManager(): NotificationManagerCompat = NotificationManagerCompat.from(service)

    @Provides
    @ServiceScope
    fun notificationBuilder() = NotificationBuilder(service)

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
        override fun stopService() {
            service.stopSelf()
        }
    }

    @Provides
    @ServiceScope
    fun plexDataSourceFactory(plexPrefsRepo: PlexPrefsRepo): DefaultDataSourceFactory {
        val httpDataSourceFactory =
            DefaultHttpDataSourceFactory(Util.getUserAgent(service, APP_NAME))

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
        props.set("X-Plex-Token", plexPrefsRepo.getAuthToken())

        return DefaultDataSourceFactory(service, httpDataSourceFactory)
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
}


