package io.github.mattpvaughn.chronicle.data.sources.local

import android.annotation.SuppressLint
import android.content.SharedPreferences
import java.util.*

/** A interface for Plex exclusive preferences */
interface LocalLibraryPrefs {
    /** Root location of the library  */
    var root: String

    /** Library name */
    var name: String

    /** Clear all preferences which are handled by PrefsRepo */
    fun clear()
}

/** An implementation of [PlexPrefsRepo] wrapping [SharedPreferences]. */
class SharedPreferencesLocalLibrary(
    localMediaSource: LocalMediaSource
) : LocalLibraryPrefs {

    val prefs = localMediaSource.prefs()

    private companion object {
        const val PREFS_ROOT_KEY = "root"
        const val PREFS_NAME_KEY = "name"
    }

    override var root: String
        get() = getString(PREFS_ROOT_KEY, "")
        @SuppressLint("ApplySharedPref")
        set(value) {
            prefs.edit().putString(PREFS_ROOT_KEY, value).commit()
        }

    override var name: String
        get() = getString(PREFS_NAME_KEY, "")
        @SuppressLint("ApplySharedPref")
        set(value) {
            prefs.edit().putString(PREFS_NAME_KEY, value).commit()
        }

    override fun clear() {
        root = ""
    }

    /**
     * Retrieve a string stored in shared preferences
     *
     * @param key the key of the item stored in preferences
     * @param defaultValue (optional) the value to return if the desired string cannot be found.
     *                     Defaults to the empty string
     *
     * @return the stored preference value corresponding to the [key] passed in. If there is no
     * corresponding value, return the default value provided
     *
     */
    private fun getString(key: String, defaultValue: String = ""): String {
        return prefs.getString(key, defaultValue) ?: defaultValue
    }
}
