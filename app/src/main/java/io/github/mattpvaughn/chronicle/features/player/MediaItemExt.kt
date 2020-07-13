package io.github.mattpvaughn.chronicle.features.player

import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
import android.support.v4.media.MediaDescriptionCompat
import androidx.annotation.DrawableRes
import io.github.mattpvaughn.chronicle.BuildConfig
import io.github.mattpvaughn.chronicle.data.sources.plex.CONTENT_STYLE_BROWSABLE_HINT
import io.github.mattpvaughn.chronicle.data.sources.plex.CONTENT_STYLE_LIST_ITEM_HINT_VALUE
import io.github.mattpvaughn.chronicle.data.sources.plex.CONTENT_STYLE_SUPPORTED

/** Create a basic browsable item for Auto */
fun makeBrowsable(
    title: String,
    @DrawableRes iconRes: Int,
    desc: String = ""
): MediaItem {
    val mediaDescription = MediaDescriptionCompat.Builder()
    mediaDescription.setTitle(title)
    mediaDescription.setSubtitle(desc)
    mediaDescription.setIconUri(
        Uri.parse("android.resource://${BuildConfig.APPLICATION_ID}/$iconRes")
    )
    mediaDescription.setMediaId(title)
    val extras = Bundle()
    extras.putBoolean(CONTENT_STYLE_SUPPORTED, true)
    extras.putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_LIST_ITEM_HINT_VALUE)
    mediaDescription.setExtras(extras)
    return MediaItem(mediaDescription.build(), FLAG_BROWSABLE)
}


