package io.github.mattpvaughn.chronicle.features.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.GET_ACTIVITIES
import android.graphics.Bitmap
import android.os.Build
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE
import android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY
import android.view.KeyEvent
import android.view.KeyEvent.KEYCODE_MEDIA_STOP
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import io.github.mattpvaughn.chronicle.BuildConfig
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.application.MainActivity.Companion.FLAG_OPEN_ACTIVITY_TO_CURRENTLY_PLAYING
import io.github.mattpvaughn.chronicle.application.MainActivity.Companion.REQUEST_CODE_OPEN_APP_TO_CURRENTLY_PLAYING
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import io.github.mattpvaughn.chronicle.data.model.EMPTY_CHAPTER
import io.github.mattpvaughn.chronicle.data.model.NO_AUDIOBOOK_FOUND_ID
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexConfig
import io.github.mattpvaughn.chronicle.features.currentlyplaying.CurrentlyPlaying
import io.github.mattpvaughn.chronicle.injection.scopes.ServiceScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import timber.log.Timber
import javax.inject.Inject

const val NOW_PLAYING_CHANNEL: String =
    "io.github.mattpvaughn.chronicle.features.player\$NOW_PLAYING_CHANNEL"
const val NOW_PLAYING_NOTIFICATION: Int = 0xb32229

/** Helper class to encapsulate code for building notifications. */
@ExperimentalCoroutinesApi
@ServiceScope
class NotificationBuilder @Inject constructor(
    private val context: Context,
    private val plexConfig: PlexConfig,
    private val controller: MediaControllerCompat,
    private val currentlyPlaying: CurrentlyPlaying,
    private val prefsRepo: PrefsRepo
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

    private fun makeJumpForwardsIcon() : Int {
        return when (prefsRepo.jumpForwardSeconds) {
            10L -> R.drawable.ic_forward_10_white
            15L -> R.drawable.ic_forward_15_white
            20L -> R.drawable.ic_forward_20_white
            30L -> R.drawable.ic_forward_30_white
            60L -> R.drawable.ic_forward_60_white
            90L -> R.drawable.ic_forward_90_white
            else -> R.drawable.ic_forward_30_white
        }
    }

    private fun skipForwardsAction() = NotificationCompat.Action(
        makeJumpForwardsIcon(),
        context.getString(R.string.skip_forwards),
        makePendingIntent(mediaSkipForwardCode)
    )

    private fun makeJumpBackwardsIcon() : Int {
        return when (prefsRepo.jumpBackwardSeconds) {
            10L -> R.drawable.ic_replay_10_white
            15L -> R.drawable.ic_replay_15_white
            20L -> R.drawable.ic_replay_20_white
            30L -> R.drawable.ic_replay_30_white
            60L -> R.drawable.ic_replay_60_white
            90L -> R.drawable.ic_replay_90_white
            else -> R.drawable.ic_replay_10_white
        }
    }

    private fun skipBackwardsAction() = NotificationCompat.Action(
        makeJumpBackwardsIcon(),
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
        contentPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_OPEN_APP_TO_CURRENTLY_PLAYING,
            intent,
            0
        )
    }

    var bookTitleBitmapPair: Pair<Int, Bitmap?>? = null

    private var currentNotificationMetadata = NotificationData(
        bookId = NO_AUDIOBOOK_FOUND_ID,
        trackId = ITrackRepository.TRACK_NOT_FOUND,
        chapterId = EMPTY_CHAPTER.id,
        playbackState = PlaybackStateCompat.STATE_NONE
    )

    private data class NotificationData(
        private val bookId: Int,
        private val trackId: Int,
        private val chapterId: Long,
        private val playbackState: Int
    )

    private val currentID = NotificationData(
        bookId = currentlyPlaying.book.value.id,
        trackId = currentlyPlaying.track.value.id,
        chapterId = currentlyPlaying.chapter.value.id,
        playbackState = PlaybackStateCompat.STATE_NONE
    )

    /**
     * Builds a notification representing the current playback state as representing by
     * [CurrentlyPlaying] and the current [MediaSessionCompat]
     *
     * @return a notification representing the current playback state or null if one already exists
     */
    suspend fun buildNotification(sessionToken: MediaSessionCompat.Token): Notification? {
        if (shouldCreateChannel()) {
            createNowPlayingChannel()
        }

        val builder = NotificationCompat.Builder(context, NOW_PLAYING_CHANNEL)
        val isPlaying = controller.playbackState.isPlaying

        if (BuildConfig.DEBUG) {
            Timber.i("Building notification! track=${currentlyPlaying.track.value.title}, index=${currentlyPlaying.track.value.index}")
            Timber.i("Building notification! chapter=${currentlyPlaying.chapter.value.title}, index=${currentlyPlaying.chapter.value.index}")
            Timber.i("Building notification! state=${controller.playbackState.stateName}, playing=$isPlaying")
        }

        builder.addAction(skipBackwardsAction())
        if (isPlaying) {
            builder.addAction(pauseAction)
        } else {
            builder.addAction(playAction)
        }

        builder.addAction(skipForwardsAction())

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

        val smallIcon = if (isPlaying) {
            R.drawable.ic_notification_icon_playing
        } else {
            R.drawable.ic_notification_icon_paused
        }

        val chapterTitle = currentlyPlaying.chapter.value.title

        // NOTE: As long as [MediaStyle.setMediaSession()] hijacks the notification,
        // title/subtitle will be pulled directly from the session, ignoring below
        val currentBook = currentlyPlaying.book.value
        val titles = if (chapterTitle.isNotEmpty()) {
            Pair(chapterTitle, currentBook.title)
        } else {
            Pair(currentBook.title, currentBook.author)
        }

        // Only load bitmap when the book changes
        if (bookTitleBitmapPair?.first != currentBook.id) {
            val artUri = currentBook.thumb
            Timber.i("Loading art uri: $artUri")
            val largeIcon = plexConfig.getBitmapFromServer(artUri)
            // ^^^ nullable, but null is expected value for book without artwork ^^^
            bookTitleBitmapPair = Pair(currentBook.id, largeIcon)
        }

        return builder.setContentTitle(titles.first)
            .setContentText(titles.second)
            .setContentIntent(controller.sessionActivity)
            .setDeleteIntent(stopPendingIntent)
            .setOnlyAlertOnce(true)
            .setSmallIcon(smallIcon)
            .setLargeIcon(bookTitleBitmapPair?.second)
            .setStyle(mediaStyle)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun shouldCreateChannel() =
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
        ).apply {
            description = context.getString(R.string.notification_channel_description)
        }

        platformNotificationManager.createNotificationChannel(notificationChannel)
    }
}
