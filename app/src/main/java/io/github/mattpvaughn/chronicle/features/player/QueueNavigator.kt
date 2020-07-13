package io.github.mattpvaughn.chronicle.features.player

import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.ext.cast.CastPlayer
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator
import javax.inject.Inject


/** A [TimelineQueueNavigator] with behavior appropriate for audiobook playback */
class QueueNavigator @Inject constructor(
    mediaSession: MediaSessionCompat
) : TimelineQueueNavigator(mediaSession) {

    private val window = Timeline.Window()

    /**
     * Prevent "skip to next/prev" from being exposed to the client.
     *
     * Note: this may need to be changed as it prevents some of the MediaControllerTest app tests
     * from passing. Depends on whether the Auto rules explicitly require this, but pretty sure it's
     * not necessary as some podcast apps support the same behavior
     */
    override fun getSupportedQueueNavigatorActions(player: Player): Long {
        return 0L
    }

    override fun getMediaDescription(player: Player, windowIndex: Int): MediaDescriptionCompat {
        return when (player) {
            is ExoPlayer -> {
                player.currentTimeline.getWindow(
                    windowIndex,
                    window
                ).tag as MediaDescriptionCompat
            }
            is CastPlayer -> {
                val periodId = player.currentTag as Int
                player.getItem(periodId)?.media?.metadata.toMediaDescriptionCompat()
            }
            else -> throw NoWhenBranchMatchedException("Unknown player: $player")
        }
    }
}

