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
    override var skipSilence: Boolean
        get() = false
        set(value) {}

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return false
    }

    override fun setBoolean(key: String, value: Boolean) {}

    override fun clearAll() {}

    override fun registerPrefsListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}

    override fun unRegisterPrefsListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}

    override var debugOnlyDisableLocalProgressTracking: Boolean
        get() = false
        set(value) {}

}