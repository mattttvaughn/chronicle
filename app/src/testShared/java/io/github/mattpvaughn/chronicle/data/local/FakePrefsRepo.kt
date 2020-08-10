package io.github.mattpvaughn.chronicle.data.local

import android.content.SharedPreferences
import java.io.File
import javax.inject.Inject

class FakePrefsRepo @Inject constructor() : PrefsRepo {
    override var cachedMediaDir: File
        get() = File("")
        set(value) {}
    override var bookCoverStyle: String
        get() = "Square"
        set(value) {}
    override var offlineMode: Boolean
        get() = false
        set(value) {}
    override var playbackSpeed: Float
        get() = 1F
        set(value) {}
    override var allowAuto: Boolean
        get() = TODO("Not yet implemented")
        set(value) {}
    override var skipSilence: Boolean
        get() = false
        set(value) {}
    override var autoRewind: Boolean
        get() = TODO("Not yet implemented")
        set(value) {}
    override var shakeToSnooze: Boolean
        get() = TODO("Not yet implemented")
        set(value) {}

    override val isPremium: Boolean
        get() = TODO("Not yet implemented")
    override var lastRefreshTimeStamp: Long
        get() = TODO("Not yet implemented")
        set(value) {}
    override var refreshRateMinutes: Long
        get() = TODO("Not yet implemented")
        set(value) {}
    override var premiumPurchaseToken: String
        get() = TODO("Not yet implemented")
        set(value) {}

    override var bookSortKey: String
        get() = TODO("Not yet implemented")
        set(value) {}
    override var libraryViewTypeKey: String
        get() = TODO("Not yet implemented")
        set(value) {}
    override var isLibrarySortedDescending: Boolean
        get() = TODO("Not yet implemented")
        set(value) {}
    override var sources: List<Pair<Long, String>>
        get() = TODO("Not yet implemented")
        set(value) {}

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return false
    }

    override fun setBoolean(key: String, value: Boolean) {}

    override fun clearAll() {}

    override fun registerPrefsListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}

    override fun unregisterPrefsListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}
    override fun containsKey(key: String): Boolean {
        TODO("Not yet implemented")
    }

    override var debugOnlyDisableLocalProgressTracking: Boolean
        get() = false
        set(value) {}

}