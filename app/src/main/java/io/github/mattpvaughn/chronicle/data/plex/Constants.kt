package io.github.mattpvaughn.chronicle.data.plex

const val MEDIA_TYPE_ALBUM = 9
const val MEDIA_TYPE_TRACK = 10
const val APP_NAME = "Chronicle"

/** ---- For android auto ---- */

/** Declares that ContentStyle is supported */
val CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"

/**
 * Bundle extra indicating the presentation hint for playable media items.
 */
val CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"

/**
 * Bundle extra indicating the presentation hint for browsable media items.
 */
val CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"

/**
 * Specifies the corresponding items should be presented as lists.
 */
val CONTENT_STYLE_LIST_ITEM_HINT_VALUE = 1

/**
 * Specifies that the corresponding items should be presented as grids.
 */
val CONTENT_STYLE_GRID_ITEM_HINT_VALUE = 2

/**
 * Bundle extra indicating that a media item is available offline.
 * Same as MediaDescriptionCompat.EXTRA_DOWNLOAD_STATUS.
 */
var EXTRA_IS_DOWNLOADED = "android.media.extra.DOWNLOAD_STATUS"

