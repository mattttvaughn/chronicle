package io.github.mattpvaughn.chronicle.data.local

import android.content.SharedPreferences
import com.android.billingclient.api.Purchase
import io.github.mattpvaughn.chronicle.BuildConfig
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.KEY_ALLOW_AUTO
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.KEY_AUTO_REWIND_ENABLED
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.KEY_BOOK_COVER_STYLE
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.KEY_BOOK_SORT_BY
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.KEY_DEBUG_DISABLE_PROGRESS
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.KEY_IS_LIBRARY_SORT_DESCENDING
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.KEY_IS_PREMIUM
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.KEY_LAST_REFRESH
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.KEY_LIBRARY_MEDIA_TYPE
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.KEY_LIBRARY_VIEW_STYLE
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.KEY_OFFLINE_MODE
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.KEY_PAUSE_ON_FOCUS_LOST
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.KEY_PLAYBACK_SPEED
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.KEY_PREMIUM_TOKEN
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.KEY_REFRESH_RATE
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.KEY_JUMP_FORWARD_SECONDS
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.KEY_JUMP_BACKWARD_SECONDS
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.KEY_SHAKE_TO_SNOOZE_ENABLED
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.KEY_SKIP_SILENCE
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.KEY_SYNC_DIR_PATH
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.NO_PREMIUM_TOKEN
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.VIEW_STYLES
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.VIEW_STYLE_COVER_GRID
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.sources.plex.model.MediaType
import io.github.mattpvaughn.chronicle.injection.components.AppComponent
import java.io.File
import javax.inject.Inject

/**
 * An interface for getting/setting persistent preferences for Chronicle
 */
interface PrefsRepo {
    /** The directory where media files are synced */
    var cachedMediaDir: File

    /** The style of book covers in the app- i.e. rectangular, square */
    var bookCoverStyle: String

    /** Whether the app should be able to access the network */
    var offlineMode: Boolean

    /** The user's preferred speed of audio playback */
    var playbackSpeed: Float

    /** Whether the user has given access to Auto */
    var allowAuto: Boolean

    /** Whether to fast-forward through silent bits of audio during playback */
    var skipSilence: Boolean

    /** Whether the app should rewind a small bit if user hasn't played an audiobook in a while */
    var autoRewind: Boolean

    /** Whether the app will extend the sleep timer upon device shake */
    var shakeToSnooze: Boolean

    /** Pause when audio focus lost */
    var pauseOnFocusLost: Boolean

    /** Whether the app should display premium features */
    val isPremium: Boolean

    /** The last time the library was refreshed, as Unix timestamp (in millis) */
    var lastRefreshTimeStamp: Long

    /** The minimum number of minutes between data refreshes*/
    var refreshRateMinutes: Long

    /** The time interval for jumping forward in the player view.*/
    var jumpForwardSeconds: Long

    /** The time interval for jumping backward in the player view.*/
    var jumpBackwardSeconds: Long

    /** The user's IAP token returned in a [Purchase] upon paying for an upgrade to premium */
    var premiumPurchaseToken: String

    /** The key by which the books in the library are sorted. One of [Audiobook.SORT_KEYS] */
    var bookSortKey: String

    /** The type of elements shown in the library view. One of [MediaType.TYPES]*/
    var libraryMediaType: String

    /** The style of view display in the library view (e.g. book cover, text only, etc.) */
    var libraryBookViewStyle: String

    /** Whether the library is sorted in descending (true) or ascending (false) order */
    var isLibrarySortedDescending: Boolean

    /**
     * Get a saved preference value corresponding to [key], providing [defaultValue] if no value
     * is already set. Return false in the case of no value already set if [defaultValue] is not
     * provided
     */
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean

    /** Save a preference with key == [key] and value == [value] to the preferences repo */
    fun setBoolean(key: String, value: Boolean)

    /** Clear all saved preferences */
    fun clearAll()

    /** Register an [SharedPreferences.OnSharedPreferenceChangeListener] */
    fun registerPrefsListener(listener: SharedPreferences.OnSharedPreferenceChangeListener)

    /** Unregister an already registered [SharedPreferences.OnSharedPreferenceChangeListener] */
    fun unregisterPrefsListener(listener: SharedPreferences.OnSharedPreferenceChangeListener)

    fun containsKey(key: String): Boolean

    /** Disable progress tracking in the local DB for debugging purposes */
    var debugOnlyDisableLocalProgressTracking: Boolean

    companion object {
        const val KEY_SYNC_DIR_PATH = "key_sync_location"
        const val KEY_BOOK_COVER_STYLE = "key_book_cover_style"
        const val KEY_APP_OPEN_COUNT = "key_app_open_count"
        const val KEY_OFFLINE_MODE = "key_offline_mode"
        const val KEY_LAST_REFRESH = "key_last_refresh"
        const val KEY_REFRESH_RATE = "key_refresh_rate"
        const val KEY_JUMP_FORWARD_SECONDS = "key_jump_forward_seconds"
        const val KEY_JUMP_BACKWARD_SECONDS = "key_jump_backward_seconds"
        const val KEY_PLAYBACK_SPEED = "key_playback_speed"
        const val KEY_DEBUG_DISABLE_PROGRESS = "debug_key_disable_local_progress"
        const val KEY_SKIP_SILENCE = "key_skip_silence"
        const val KEY_AUTO_REWIND_ENABLED = "key_auto_rewind_enabled"
        const val KEY_ALLOW_AUTO = "key_allow_auto"
        const val KEY_SHAKE_TO_SNOOZE_ENABLED = "key_shake_to_snooze_enabled"
        const val KEY_PAUSE_ON_FOCUS_LOST = "key_pause_on_focus_lost"
        const val KEY_IS_PREMIUM = "key_is_premium"
        const val NO_PREMIUM_TOKEN = "no premium token"
        const val KEY_PREMIUM_TOKEN = "key_premium_token"
        const val KEY_BOOK_SORT_BY = "key_sort_by"
        const val KEY_IS_LIBRARY_SORT_DESCENDING = "key_is_sort_descending"
        const val KEY_LIBRARY_MEDIA_TYPE = "key_media_type"
        const val KEY_LIBRARY_VIEW_STYLE = "key_library_view_style"
        const val VIEW_STYLE_COVER_GRID = "view_style_cover_grid"
        const val VIEW_STYLE_TEXT_LIST = "view_style_text_list"
        const val VIEW_STYLE_DETAILS_LIST = "view_style_details_list"
        val VIEW_STYLES = listOf(
            VIEW_STYLE_COVER_GRID,
            VIEW_STYLE_DETAILS_LIST,
            VIEW_STYLE_TEXT_LIST
        )

        const val BOOK_COVER_STYLE_RECT = "Rectangular"
        const val BOOK_COVER_STYLE_SQUARE = "Square"
    }
}

/**
 *  An implementation of [PrefsRepo] wrapping [SharedPreferences]
 */
class SharedPreferencesPrefsRepo @Inject constructor(private val sharedPreferences: SharedPreferences) :
    PrefsRepo {
    override var cachedMediaDir: File
        get() {
            val syncLoc = sharedPreferences.getString(KEY_SYNC_DIR_PATH, "")
            return if (syncLoc.isNullOrEmpty()) {
                /** Set default location to first location in [AppComponent.externalDeviceDirs] */
                val deviceStorage = Injector.get().externalDeviceDirs().first()
                sharedPreferences.edit()
                    .putString(KEY_SYNC_DIR_PATH, deviceStorage.absolutePath)
                    .apply()
                deviceStorage
            } else {
                Injector.get().externalDeviceDirs()
                    .firstOrNull { it.absolutePath == syncLoc }
                    ?: Injector.get().externalDeviceDirs().first()
            }
        }
        set(value) = sharedPreferences.edit().putString(
            KEY_SYNC_DIR_PATH,
            value.absolutePath
        ).apply()

    private val defaultBookCoverStyle = "Rectangular"
    override var bookCoverStyle: String
        get() = getString(KEY_BOOK_COVER_STYLE, defaultBookCoverStyle)
        set(value) = sharedPreferences.edit().putString(KEY_BOOK_COVER_STYLE, value).apply()

    private val defaultOfflineMode = false
    override var offlineMode: Boolean
        get() = sharedPreferences.getBoolean(KEY_OFFLINE_MODE, defaultOfflineMode)
        set(value) = sharedPreferences.edit().putBoolean(KEY_OFFLINE_MODE, value).apply()

    private val defaultLastRefreshTimeStamp = System.currentTimeMillis()
    override var lastRefreshTimeStamp: Long
        get() = sharedPreferences.getLong(KEY_LAST_REFRESH, defaultLastRefreshTimeStamp)
        set(value) = sharedPreferences.edit().putLong(KEY_LAST_REFRESH, value).apply()

    private val defaultRefreshRate = 60L
    override var refreshRateMinutes: Long
        get() = sharedPreferences.getLong(KEY_REFRESH_RATE, defaultRefreshRate)
        set(value) = sharedPreferences.edit().putLong(KEY_REFRESH_RATE, value).apply()

    private val defaultJumpForwardSeconds = 30L
    override var jumpForwardSeconds: Long
        get() = sharedPreferences.getLong(KEY_JUMP_FORWARD_SECONDS, defaultJumpForwardSeconds)
        set(value) = sharedPreferences.edit().putLong(KEY_JUMP_FORWARD_SECONDS, value).apply()

    private val defaultJumpBackwardSeconds = 30L
    override var jumpBackwardSeconds: Long
        get() = sharedPreferences.getLong(KEY_JUMP_BACKWARD_SECONDS, defaultJumpBackwardSeconds)
        set(value) = sharedPreferences.edit().putLong(KEY_JUMP_BACKWARD_SECONDS, value).apply()

    private val defaultPlaybackSpeed = 1.0f
    override var playbackSpeed: Float
        get() = sharedPreferences.getFloat(KEY_PLAYBACK_SPEED, defaultPlaybackSpeed)
        set(value) = sharedPreferences.edit().putFloat(KEY_PLAYBACK_SPEED, value).apply()

    private val defaultSkipSilence = false
    override var skipSilence: Boolean
        get() = sharedPreferences.getBoolean(KEY_SKIP_SILENCE, defaultSkipSilence)
        set(value) = sharedPreferences.edit().putBoolean(KEY_SKIP_SILENCE, value).apply()

    private val defaultAutoRewind = true
    override var autoRewind: Boolean
        get() = sharedPreferences.getBoolean(KEY_AUTO_REWIND_ENABLED, defaultAutoRewind)
        set(value) = sharedPreferences.edit().putBoolean(KEY_AUTO_REWIND_ENABLED, value).apply()

    private val defaultShakeToSnooze = true
    override var shakeToSnooze: Boolean
        get() = sharedPreferences.getBoolean(KEY_SHAKE_TO_SNOOZE_ENABLED, defaultShakeToSnooze)
        set(value) = sharedPreferences.edit().putBoolean(KEY_SHAKE_TO_SNOOZE_ENABLED, value).apply()

    private val defaultPauseOnFocusLost = true
    override var pauseOnFocusLost: Boolean
        get() = sharedPreferences.getBoolean(KEY_PAUSE_ON_FOCUS_LOST, defaultPauseOnFocusLost)
        set(value) = sharedPreferences.edit().putBoolean(KEY_PAUSE_ON_FOCUS_LOST, value).apply()

    private val defaultAllowAuto = true
    override var allowAuto: Boolean
        get() = sharedPreferences.getBoolean(KEY_ALLOW_AUTO, defaultAllowAuto)
        set(value) = sharedPreferences.edit().putBoolean(KEY_ALLOW_AUTO, value).apply()

    private val defaultIsPremium = false
    override val isPremium: Boolean
        get() = sharedPreferences.getBoolean(KEY_IS_PREMIUM, defaultIsPremium) || BuildConfig.DEBUG

    private val defaultPremiumToken = NO_PREMIUM_TOKEN
    override var premiumPurchaseToken: String
        get() = getString(KEY_PREMIUM_TOKEN, defaultPremiumToken)
        set(value) {
            sharedPreferences.edit().putString(KEY_PREMIUM_TOKEN, value).apply()
            sharedPreferences.edit().putBoolean(KEY_IS_PREMIUM, value != NO_PREMIUM_TOKEN).apply()
        }

    private val defaultBookSortKey = Audiobook.SORT_KEY_TITLE
    override var bookSortKey: String
        get() = getString(KEY_BOOK_SORT_BY, defaultBookSortKey)
        set(value) {
            if (value !in Audiobook.SORT_KEYS) {
                throw IllegalArgumentException("Unknown sort key: $value")
            }
            sharedPreferences.edit().putString(KEY_BOOK_SORT_BY, value).apply()
        }

    private val defaultIsLibrarySortDescending = true
    override var isLibrarySortedDescending: Boolean
        get() = getBoolean(KEY_IS_LIBRARY_SORT_DESCENDING, defaultIsLibrarySortDescending)
        set(value) {
            sharedPreferences.edit().putBoolean(KEY_IS_LIBRARY_SORT_DESCENDING, value).apply()
        }

    private val viewTypeBook = "book"
    private val viewTypeAuthor = "author"
    private val viewTypeFolder = "folder"
    private val viewTypeCollection = "collection"
    private val viewTypes = listOf(viewTypeBook, viewTypeAuthor, viewTypeFolder, viewTypeCollection)
    private val defaultLibraryViewType = viewTypeBook
    override var libraryMediaType: String
        get() = getString(KEY_LIBRARY_MEDIA_TYPE, defaultLibraryViewType)
        set(value) {
            if (value !in viewTypes) {
                throw IllegalArgumentException("Unknown view type key: $value")
            }
            sharedPreferences.edit().putString(KEY_LIBRARY_MEDIA_TYPE, value).apply()
        }


    private val defaultLibraryViewStyle = VIEW_STYLE_COVER_GRID
    override var libraryBookViewStyle: String
        get() = getString(KEY_LIBRARY_VIEW_STYLE, defaultLibraryViewStyle)
        set(value) {
            if (value !in VIEW_STYLES) {
                throw IllegalArgumentException("Unknown view type key: $value")
            }
            sharedPreferences.edit().putString(KEY_LIBRARY_VIEW_STYLE, value).apply()
        }

    private val debugDisableLocalProgressTracking = false
    override var debugOnlyDisableLocalProgressTracking: Boolean
        get() = sharedPreferences.getBoolean(
            KEY_DEBUG_DISABLE_PROGRESS,
            debugDisableLocalProgressTracking
        )
        set(value) = sharedPreferences.edit().putBoolean(KEY_DEBUG_DISABLE_PROGRESS, value).apply()

    override fun setBoolean(key: String, value: Boolean) {
        sharedPreferences.edit().putBoolean(key, value).apply()
    }

    private fun getString(key: String, defaultValue: String): String {
        return sharedPreferences.getString(key, defaultValue) ?: defaultValue
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return sharedPreferences.getBoolean(key, defaultValue)
    }

    override fun clearAll() {
        sharedPreferences.edit().clear().apply()
    }

    override fun registerPrefsListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun unregisterPrefsListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    override fun containsKey(key: String): Boolean {
        return sharedPreferences.contains(key)
    }
}
