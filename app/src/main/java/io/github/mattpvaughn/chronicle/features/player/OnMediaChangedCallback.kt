package io.github.mattpvaughn.chronicle.features.player

import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.session.PlaybackStateCompat.*
import androidx.core.app.NotificationManagerCompat
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.data.local.IBookRepository
import io.github.mattpvaughn.chronicle.data.sources.SourceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/** Responsible for observing changes in media metadata */
class OnMediaChangedCallback @Inject constructor(
    private val mediaController: MediaControllerCompat,
    private val serviceScope: CoroutineScope,
    private val notificationBuilder: NotificationBuilder,
    private val mediaSession: MediaSessionCompat,
    private val becomingNoisyReceiver: BecomingNoisyReceiver,
    private val notificationManager: NotificationManagerCompat,
    private val foregroundServiceController: ForegroundServiceController,
    private val serviceController: ServiceController,
    private val bookRepository: IBookRepository,
    private val sourceManager: SourceManager
) : MediaControllerCompat.Callback() {

    var sourceId: Long? = null

    override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
        Timber.i("METADATA CHANGE")
        mediaController.playbackState?.let { state ->
            serviceScope.launch(Injector.get().unhandledExceptionHandler()) {
                updateNotification(state.state)
                withContext(Dispatchers.IO) {
                    val bookId = metadata?.description?.mediaId?.toIntOrNull() ?: return@withContext
                    val book = bookRepository.getAudiobookAsync(bookId) ?: return@withContext
                    sourceId = book.source
                }
            }
        }
    }

    override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
        Timber.i("Playback state changed ${System.currentTimeMillis()}")
        if (state == null) {
            return
        }
        serviceScope.launch(Injector.get().unhandledExceptionHandler()) {
            updateNotification(state.state)
        }
    }

    private suspend fun updateNotification(state: Int) {
        val source = sourceId?.let { sourceManager.getSourceById(it) }
        val notification = if (mediaController.metadata != null && source != null) {
            notificationBuilder.buildNotification(mediaSession.sessionToken, source)
        } else {
            null
        }

        when (state) {
            STATE_PLAYING, STATE_BUFFERING, STATE_CONNECTING -> {
                becomingNoisyReceiver.register()
                if (notification != null) {
                    notificationManager.notify(NOW_PLAYING_NOTIFICATION, notification)

                    foregroundServiceController.startForeground(
                        NOW_PLAYING_NOTIFICATION,
                        notification
                    )
                }
            }
            STATE_PAUSED -> {
                becomingNoisyReceiver.unregister()
                if (notification != null) {
                    notificationManager.notify(NOW_PLAYING_NOTIFICATION, notification)
                }
                // Enables dismiss-on-swipe
                foregroundServiceController.stopForeground(false)
            }
            STATE_STOPPED -> {
                // If playback has ended, fully stop the service.
                Timber.i("Playback has finished, stopping service!")
                notificationManager.cancel(NOW_PLAYING_NOTIFICATION)
                foregroundServiceController.stopForeground(true)
                serviceController.stopService()
            }
            else -> {
                // When not actively playing media, notification becomes cancellable on swipe and
                // we stop listening for audio interruptions
                becomingNoisyReceiver.unregister()
                foregroundServiceController.stopForeground(true)
            }
        }
    }
}
