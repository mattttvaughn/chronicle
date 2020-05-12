package io.github.mattpvaughn.chronicle.data.local

import android.content.SharedPreferences
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.KEY_BOOK_COVER_STYLE
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.KEY_DEBUG_DISABLE_PROGRESS
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.KEY_IS_PREMIUM
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.KEY_OFFLINE_MODE
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.KEY_PLAYBACK_SPEED
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.KEY_PREMIUM_TOKEN
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.KEY_SKIP_SILENCE
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.KEY_SYNC_DIR_PATH
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.NO_PREMIUM_TOKEN
import io.github.mattpvaughn.chronicle.injection.components.AppComponent
import java.io.File
import javax.inject.Inject

/**
 * An interface for getting/setting persistent preferences for Chronicle
 */
interface PrefsRepo {
    /** The directory where media files are synced */
    var cachedMediaDir: File

    /** The style of book covers in the app- i.e. rectanglular, square, ... */
    var bookCoverStyle: String

    /** Whether the app should be able to access the network */
    var offlineMode: Boolean

    /** The user's preferred speed of audio playback */
    var playbackSpeed: Float

    /** Whether to fast-forward through silent bits of audio during playback */
    var skipSilence: Boolean

    /** Whether the app should display premium features */
    val isPremium: Boolean

    /** The user's IAP token returned in a [Purchase] upon paying for an upgrade to premium */
    var premiumPurchaseToken: String

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

    fun registerPrefsListener(listener: SharedPreferences.OnSharedPreferenceChangeListener)

    fun unRegisterPrefsListener(listener: SharedPreferences.OnSharedPreferenceChangeListener)

    /** Disable progress tracking in the local DB for debugging purposes */
    var debugOnlyDisableLocalProgressTracking: Boolean

    companion object {
        const val KEY_SYNC_DIR_PATH = "key_sync_location"
        const val KEY_BOOK_COVER_STYLE = "key_book_cover_style"
        const val KEY_OFFLINE_MODE = "key_offline_mode"
        const val KEY_PLAYBACK_SPEED = "key_playback_speed"
        const val KEY_DEBUG_DISABLE_PROGRESS = "debug_key_disable_local_progress"
        const val KEY_SKIP_SILENCE = "key_skip_silence"
        const val KEY_IS_PREMIUM = "key_is_premium"
        const val NO_PREMIUM_TOKEN = "no premium token"
        const val KEY_PREMIUM_TOKEN = "key_premium_token"
    }
}

/**
 *  An implementation of [PrefsRepo] wrapping [SharedPreferences]
 */
class SharedPreferencesPrefsRepo @Inject constructor(private val sharedPreferences: SharedPreferences) :
    PrefsRepo {
    override var cachedMediaDir: File
        get() {
            val syncLoc = sharedPreferences.getString(KEY_SYNC_DIR_PATH, "")?.ifEmpty {
                /** Set default location to [AppComponent.externalDeviceDirs] */
                val deviceStorage = Injector.get().externalDeviceDirs()[0]
                sharedPreferences.edit().putString(KEY_SYNC_DIR_PATH, deviceStorage.absolutePath)
                    .apply()
                return deviceStorage
            }
            return Injector.get().externalDeviceDirs().firstOrNull { it.absolutePath == syncLoc }
                ?: Injector.get().externalDeviceDirs().first()
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

    private val defaultPlaybackSpeed = 1.0f
    override var playbackSpeed: Float
        get() = sharedPreferences.getFloat(KEY_PLAYBACK_SPEED, defaultPlaybackSpeed)
        set(value) = sharedPreferences.edit().putFloat(KEY_PLAYBACK_SPEED, value).apply()

    private val defaultSkipSilence = false
    override var skipSilence: Boolean
        get() = sharedPreferences.getBoolean(KEY_SKIP_SILENCE, defaultSkipSilence)
        set(value) = sharedPreferences.edit().putBoolean(KEY_SKIP_SILENCE, value).apply()

    private val defaultIsPremium = false
    override val isPremium: Boolean
        get() = sharedPreferences.getBoolean(KEY_IS_PREMIUM, defaultIsPremium)

    private val defaultPremiumToken = NO_PREMIUM_TOKEN
    override var premiumPurchaseToken: String
        get() = getString(KEY_PREMIUM_TOKEN, defaultPremiumToken)
        set(value) {
            sharedPreferences.edit().putString(KEY_PREMIUM_TOKEN, value).apply()
            sharedPreferences.edit().putBoolean(KEY_IS_PREMIUM, value != NO_PREMIUM_TOKEN).apply()
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

    override fun unRegisterPrefsListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
