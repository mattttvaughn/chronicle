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
import android.graphics.Bitmap
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
import io.github.mattpvaughn.chronicle.data.local.IBookRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository.Companion.TRACK_NOT_FOUND
import io.github.mattpvaughn.chronicle.data.model.asChapterList
import io.github.mattpvaughn.chronicle.data.model.getActiveTrack
import io.github.mattpvaughn.chronicle.data.model.getChapterAt
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexConfig
import io.github.mattpvaughn.chronicle.injection.scopes.ServiceScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

const val NOW_PLAYING_CHANNEL: String = "io.github.mattpvaughn.chronicle"
const val NOW_PLAYING_NOTIFICATION: Int = 0xb32229

/** Helper class to encapsulate code for building notifications. */
@ServiceScope
class NotificationBuilder @Inject constructor(
    private val context: Context,
    private val controller: MediaControllerCompat,
    private val session: MediaSessionCompat,
    private val plexConfig: PlexConfig,
    private val trackRepo: ITrackRepository,
    private val bookRepo: IBookRepository
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

    var bookTitleBitmapPair: Pair<String, Bitmap?>? = null

    suspend fun buildNotification(sessionToken: MediaSessionCompat.Token): Notification {
        if (shouldCreateNowPlayingChannel()) {
            createNowPlayingChannel()
        }

        val description = controller.metadata.description
        val playbackState = controller.playbackState

        val builder = NotificationCompat.Builder(context, NOW_PLAYING_CHANNEL)

        // Only add actions depending on playback status
        builder.addAction(skipBackwardsAction)
        if (playbackState.isPlaying) {
            builder.addAction(pauseAction)
        } else if (playbackState.isPlayEnabled) {
            builder.addAction(playAction)
        }
        builder.addAction(skipForwardsAction)
        // Add a button to manually kill the notification + service
        builder.addAction(
            R.drawable.ic_close_white,
            context.getString(R.string.cancel),
            stopPendingIntent
        )

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

        val trackId: Int = try {
            session.controller.metadata.id?.toInt() ?: TRACK_NOT_FOUND
        } catch (t: Throwable) {
            TRACK_NOT_FOUND
        }
        Timber.i("Track id is: $trackId")
        val bookId = withContext(Dispatchers.IO) { trackRepo.getBookIdForTrack(trackId) }
        val book = withContext(Dispatchers.IO) { bookRepo.getAudiobookAsync(bookId) }
        Timber.i("Book is $book")
        val tracks = withContext(Dispatchers.IO) {
            trackRepo.getTracksForAudiobookAsync(bookId)
        }
        Timber.i("Tracks are $tracks")
        val chapterTitle = if (book != null && tracks.isNotEmpty()) {
            val chapters = book.chapters.takeIf { it.isNotEmpty() } ?: tracks.asChapterList()
            val activeTrack = tracks.getActiveTrack()
            val currentTrackProgress: Long = activeTrack.progress
            chapters.filter {
                it.trackId.toInt() == activeTrack.id
            }.getChapterAt(currentTrackProgress).title
        } else {
            ""
        }
        Timber.i("Chapter title is: $chapterTitle")
        val title = chapterTitle.takeIf { it.isNotEmpty() } ?: description.title
        val subtitle = if (book != null && chapterTitle.isNotEmpty()) {
            // if we have a chapter title, the subtitle should be book name
            book.title
        } else {
            // o/w use the author name from [description]
            description.subtitle
        }
        Timber.i("Title is: $title")
        Timber.i("Subtitle is: $subtitle")

        // Because I'm not sure which one I usually set
        val artUri = controller.metadata.albumArtUri.takeIf { it != Uri.EMPTY }
            ?: controller.metadata.artUri.takeIf { it != Uri.EMPTY }
            ?: controller.metadata.displayIconUri
        Timber.i("Art uri is $artUri")
        // Only load bitmap on changes to the title
        if (bookTitleBitmapPair?.first != description.title) {
            Timber.i("Loading art uri")
            val largeIcon = plexConfig.getBitmapFromServer(artUri.toString())
            if (largeIcon != null) {
                bookTitleBitmapPair = Pair(description?.title?.toString() ?: "", largeIcon)
            }
        }

        builder.setContentText(subtitle)
            .setContentTitle(title)
            .setContentIntent(controller.sessionActivity)
            .setDeleteIntent(stopPendingIntent)
            .setOnlyAlertOnce(true)
            .setSmallIcon(smallIcon)
            .setLargeIcon(bookTitleBitmapPair?.second)
            .setStyle(mediaStyle)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        return builder.build()
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
