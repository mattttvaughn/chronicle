package io.github.mattpvaughn.chronicle.features.player

import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationManagerCompat
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository.Companion.TRACK_NOT_FOUND
import io.github.mattpvaughn.chronicle.data.model.getProgress
import io.github.mattpvaughn.chronicle.data.plex.model.getDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class MediaControllerCallback(
    private val mediaController: MediaControllerCompat,
    private val serviceScope: CoroutineScope,
    private val trackRepository: ITrackRepository,
    private val progressUpdater: ProgressUpdater,
    private val notificationBuilder: NotificationBuilder,
    private val mediaSession: MediaSessionCompat,
    private val becomingNoisyReceiver: BecomingNoisyReceiver,
    private val notificationManager: NotificationManagerCompat,
    private val foregroundServiceController: ForegroundServiceController,
    private val serviceController: ServiceController
) : MediaControllerCompat.Callback() {
    override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
        mediaController.playbackState?.let { state ->
            serviceScope.launch {
                updateNotification(state.state)
            }
        }
    }

    override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
        state?.let {
            serviceScope.launch {
                updateNotification(it.state)
            }
        }
        // Update track progress
        val trackId = mediaController.metadata.id
        if (trackId != null && trackId != TRACK_NOT_FOUND.toString() && state != null) {
            val stateString =
                if (state.isPlaying) MediaPlayerService.PLEX_STATE_PLAYING else MediaPlayerService.PLEX_STATE_PAUSED
            serviceScope.launch {
                val bookId = trackRepository.getBookIdForTrack(trackId.toInt())
                val tracks = trackRepository.getTracksForAudiobookAsync(bookId)
                val isBookFinished = tracks.getDuration() == tracks.getProgress()
                if (isBookFinished) {
                    mediaController.transportControls.stop()
                }
                progressUpdater.updateProgress(
                    trackId.toInt(),
                    stateString,
                    state.currentPlayBackPosition
                )
            }
        }
    }

    private suspend fun updateNotification(state: Int) {

        val notification =
            if (mediaController.metadata != null && state != PlaybackStateCompat.STATE_NONE) {
                notificationBuilder.buildNotification(mediaSession.sessionToken)
            } else {
                null
            }

        when (state) {
            PlaybackStateCompat.STATE_BUFFERING, PlaybackStateCompat.STATE_PLAYING -> {
                becomingNoisyReceiver.register()
                if (notification != null) {
                    notificationManager.notify(NOW_PLAYING_NOTIFICATION, notification)

                    if (!foregroundServiceController.isForegroundServiceActive()) {
                        serviceController.startService()
                        foregroundServiceController.startForeground(
                            NOW_PLAYING_NOTIFICATION,
                            notification
                        )
                        foregroundServiceController.setForegroundServiceActive(true)
                    }
                }
            }
            else -> {
                becomingNoisyReceiver.unregister()

                if (foregroundServiceController.isForegroundServiceActive()) {
                    foregroundServiceController.stopForeground(false)
                    foregroundServiceController.setForegroundServiceActive(false)

                    // If playback has ended, also stop the service.
                    if (state == PlaybackStateCompat.STATE_NONE) {
                        serviceController.stopService()
                    }

                    if (notification != null) {
                        notificationManager.notify(NOW_PLAYING_NOTIFICATION, notification)
                    } else {
                        notificationManager.cancel(NOW_PLAYING_NOTIFICATION)
                    }
                }
            }
        }
    }
}
