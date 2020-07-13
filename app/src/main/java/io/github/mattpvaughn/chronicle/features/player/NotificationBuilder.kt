package io.github.mattpvaughn.chronicle.features.player

/*
 * Copyright 2018 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.GET_ACTIVITIES
import android.net.Uri
import android.os.Build
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE
import android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY
import android.view.KeyEvent
import android.view.KeyEvent.KEYCODE_MEDIA_STOP
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.application.MainActivity.Companion.FLAG_OPEN_ACTIVITY_TO_CURRENTLY_PLAYING
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexConfig
import io.github.mattpvaughn.chronicle.injection.scopes.ServiceScope
import kotlinx.coroutines.coroutineScope
import timber.log.Timber

const val NOW_PLAYING_CHANNEL: String = "io.github.mattpvaughn.chronicle"
const val NOW_PLAYING_NOTIFICATION: Int = 0xb32229

/** Helper class to encapsulate code for building notifications. */
@ServiceScope
class NotificationBuilder(
    private val context: Context,
    private val controller: MediaControllerCompat,
    private val plexConfig: PlexConfig
) {

    private val platformNotificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val playAction = NotificationCompat.Action(
        R.drawable.exo_controls_play,
        context.getString(R.string.notification_play),
        MediaButtonReceiver.buildMediaButtonPendingIntent(context, ACTION_PLAY)
    )
    private val pauseAction = NotificationCompat.Action(
        R.drawable.exo_controls_pause,
        context.getString(R.string.notification_pause),
        MediaButtonReceiver.buildMediaButtonPendingIntent(context, ACTION_PAUSE)
    )
    private val skipForwardsAction = NotificationCompat.Action(
        R.drawable.ic_forward_30_white,
        context.getString(R.string.skip_forwards),
        makePendingIntent(mediaSkipForwardCode)
    )
    private val skipBackwardsAction = NotificationCompat.Action(
        R.drawable.ic_replay_10_white,
        context.getString(R.string.skip_backwards),
        makePendingIntent(mediaSkipBackwardCode)
    )

    private fun makePendingIntent(keycode: Int): PendingIntent? {
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
        intent.setPackage(context.packageName)
        intent.component = ComponentName(
            context.packageName,
            MediaPlayerService::class.qualifiedName
                ?: "io.github.mattpvaughn.chronicle.features.player.MediaPlayerService"
        )
        intent.putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keycode))
        return PendingIntent.getService(context, keycode, intent, 0)
    }

    private val stopPendingIntent = makePendingIntent(KEYCODE_MEDIA_STOP)

    private val contentPendingIntent: PendingIntent

    init {
        val intent = Intent()
        val activity = context.packageManager.getPackageInfo(context.packageName, GET_ACTIVITIES)
            .activities.find { it.name.contains("MainActivity") }
        intent.setPackage(context.packageName)
        intent.putExtra(FLAG_OPEN_ACTIVITY_TO_CURRENTLY_PLAYING, true)
        intent.component = ComponentName(context.packageName, activity?.name ?: "")
        contentPendingIntent = PendingIntent.getActivity(context, -1, intent, 0)
    }

    suspend fun buildNotification(sessionToken: MediaSessionCompat.Token): Notification {
        return coroutineScope {
            if (shouldCreateNowPlayingChannel()) {
                createNowPlayingChannel()
            }

            val description = controller.metadata.description
            val playbackState = controller.playbackState

            val builder = NotificationCompat.Builder(context, NOW_PLAYING_CHANNEL)

            // Only add actions for skip back, play/pause, skip forward, based on what's enabled.
            builder.addAction(skipBackwardsAction)
            if (playbackState.isPlaying) {
                builder.addAction(pauseAction)
            } else if (playbackState.isPlayEnabled) {
                builder.addAction(playAction)
            }
            builder.addAction(skipForwardsAction)

            val mediaStyle = MediaStyle()
                .setCancelButtonIntent(stopPendingIntent)
                .setMediaSession(sessionToken)
                .setShowActionsInCompactView(0, 1, 2)
                .setShowCancelButton(true)

            val smallIcon = if (playbackState.isPlaying) {
                R.drawable.ic_notification_icon_playing
            } else {
                R.drawable.ic_notification_icon_paused
            }

            val artUri = controller.metadata.albumArtUri.takeIf { it != Uri.EMPTY }
                ?: controller.metadata.artUri.takeIf { it != Uri.EMPTY }
                ?: controller.metadata.displayIconUri
            Timber.i("Art uri is $artUri")
            val largeIcon = plexConfig.getBitmapFromServer(artUri.toString())

            builder.setContentText(description.subtitle)
                .setContentTitle(description.title)
                .setContentIntent(controller.sessionActivity)
                .setDeleteIntent(stopPendingIntent)
                .setOnlyAlertOnce(true)
                .setSmallIcon(smallIcon)
                .setLargeIcon(largeIcon)
                .setStyle(mediaStyle)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            builder.build()
        }
    }

    private fun shouldCreateNowPlayingChannel() =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !nowPlayingChannelExists()

    @RequiresApi(Build.VERSION_CODES.O)
    private fun nowPlayingChannelExists() =
        platformNotificationManager.getNotificationChannel(NOW_PLAYING_CHANNEL) != null

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNowPlayingChannel() {
        val notificationChannel = NotificationChannel(
            NOW_PLAYING_CHANNEL,
            context.getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
            .apply {
                description = context.getString(R.string.notification_channel_description)
            }

        platformNotificationManager.createNotificationChannel(notificationChannel)
    }
}
