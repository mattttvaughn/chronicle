package io.github.mattpvaughn.chronicle.injection.components

import android.app.PendingIntent
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import dagger.Component
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexMediaRepository
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexMediaSource
import io.github.mattpvaughn.chronicle.features.player.*
import io.github.mattpvaughn.chronicle.injection.modules.ServiceModule
import io.github.mattpvaughn.chronicle.injection.scopes.ServiceScope
import io.github.mattpvaughn.chronicle.util.PackageValidator
import kotlinx.coroutines.CoroutineScope

@ServiceScope
@Component(dependencies = [AppComponent::class], modules = [ServiceModule::class])
interface ServiceComponent {
    fun progressUpdater(): ProgressUpdater
    fun exoPlayer(): ExoPlayer
    fun mediaSession(): MediaSessionCompat
    fun pendingIntent(): PendingIntent
    fun sleepTimer(): SleepTimer
    fun localBroadcastManager(): LocalBroadcastManager
    fun notificationManager(): NotificationManagerCompat
    fun notificationBuilder(): NotificationBuilder
    fun becomingNoisyReceiver(): BecomingNoisyReceiver
    fun mediaSessionCallback(): AudiobookMediaSessionCallback
    fun mediaSource(): PlexMediaRepository
    fun mediaSessionConnector(): MediaSessionConnector
    fun serviceScope(): CoroutineScope
    fun serviceController(): ServiceController
    fun plexDataSourceFactory(): DefaultHttpDataSource.Factory
    fun packageValidator(): PackageValidator
    fun foregroundServiceController(): ForegroundServiceController
    fun trackListManager(): TrackListStateManager
    fun mediaController(): MediaControllerCompat
    fun plexMediaSource(): PlexMediaSource

    fun inject(mediaPlayerService: MediaPlayerService)
}
