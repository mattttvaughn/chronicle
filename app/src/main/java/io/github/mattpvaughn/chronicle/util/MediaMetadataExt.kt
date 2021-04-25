package io.github.mattpvaughn.chronicle.util

import android.media.MediaMetadataRetriever

fun MediaMetadataRetriever.extractColumnNullable(keycode: Int): String? {
    return extractMetadata(keycode)
}