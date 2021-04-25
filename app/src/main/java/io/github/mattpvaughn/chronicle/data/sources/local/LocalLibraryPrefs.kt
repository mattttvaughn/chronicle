package io.github.mattpvaughn.chronicle.data.sources.local

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.net.toUri
import java.util.*
import javax.inject.Inject

/** A interface for Plex exclusive preferences */
interface LocalLibraryPrefs {

    /** Name identifying the library */
    var name: String

    /** The root directory of the library */
    var root: Uri?

    /** Clear all preferences which are handled by PrefsRepo */
    fun clear()
}

/** An implementation of [PlexPrefsRepo] wrapping [SharedPreferences]. */
class SharedPreferencesLocalLibrary @Inject constructor(
    private val prefs: SharedPreferences,
) : LocalLibraryPrefs {

    private companion object {
        const val PREFS_NAME = "name"
        const val PREFS_ROOT_DIRECTORY = "root_directory"
        const val NO_TEMP_ID_FOUND = -1L
    }

    override fun clear() {
        name = ""
        root = null
    }

    override var name: String
        @SuppressLint("ApplySharedPref")
        get() = getString(PREFS_NAME, "Local library")
        set(value) {
            prefs.edit().putString(PREFS_NAME, value).commit()
        }

    override var root: Uri?
        @SuppressLint("ApplySharedPref")
        get() {
            val rootPath = getString(PREFS_ROOT_DIRECTORY, "")
            if (rootPath.isEmpty()) {
                return null
            }
            return rootPath.toUri()
        }
        set(value) {
            val path = value?.toString()
            if (path != null) {
                prefs.edit().putString(PREFS_ROOT_DIRECTORY, path).commit()
            }
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
