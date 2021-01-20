package io.github.mattpvaughn.chronicle.data

const val MEDIA_TYPE_ALBUM = 9
const val MEDIA_TYPE_TRACK = 10
const val APP_NAME = "Chronicle"

/** ---- For android auto ---- */

/** Declares that ContentStyle is supported */
const val CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"

/**
 * Bundle extra indicating the presentation hint for playable media items.
 */
const val CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"

/**
 * Bundle extra indicating the presentation hint for browsable media items.
 */
val CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"

/**
 * Specifies the corresponding items should be presented as lists.
 */
const val CONTENT_STYLE_LIST_ITEM_HINT_VALUE = 1

/**
 * Specifies that the corresponding items should be presented as grids.
 */
const val CONTENT_STYLE_GRID_ITEM_HINT_VALUE = 2

/**
 * Bundle extra indicating that a media item is available offline.
 * Same as MediaDescriptionCompat.EXTRA_DOWNLOAD_STATUS.
 */
const val EXTRA_IS_DOWNLOADED = "android.media.extra.DOWNLOAD_STATUS"

/**
 * Bundle extra indicating the played state of long-form content (such as podcast
 * episodes or audiobooks).
 */
const val EXTRA_PLAY_COMPLETION_STATE = "android.media.extra.PLAYBACK_STATUS"

/**
 * Value for EXTRA_PLAY_COMPLETION_STATE that indicates the media item has
 * not been played at all.
 */
const val STATUS_NOT_PLAYED = 0

/**
 * Value for EXTRA_PLAY_COMPLETION_STATE that indicates the media item has
 * been partially played (i.e. the current position is somewhere in the middle).
 */
const val STATUS_PARTIALLY_PLAYED = 1

/**
 * Value for EXTRA_PLAY_COMPLETION_STATE that indicates the media item has
 * been completed.
 */
const val STATUS_FULLY_PLAYED = 2


