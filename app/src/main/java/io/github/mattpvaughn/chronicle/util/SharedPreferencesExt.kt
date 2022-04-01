package io.github.mattpvaughn.chronicle.util

import android.content.SharedPreferences
import androidx.lifecycle.LiveData

/** Exposes a boolean in [SharedPreferences] as [LiveData] */
class BooleanPreferenceLiveData(
    private val key: String,
    private val defaultValue: Boolean,
    private val sharedPreferences: SharedPreferences
) : LiveData<Boolean>() {
    private val prefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == this@BooleanPreferenceLiveData.key) {
                sharedPreferences?.getBoolean(key, defaultValue)?.let {
                    value = it
                }
            }
        }

    override fun onActive() {
        sharedPreferences.registerOnSharedPreferenceChangeListener(prefListener)
        value = sharedPreferences.getBoolean(key, defaultValue)
    }

    override fun onInactive() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(prefListener)
    }
}

/** Exposes a string in [SharedPreferences] as [LiveData] */
class StringPreferenceLiveData(
    private val key: String,
    private val defaultValue: String,
    private val sharedPreferences: SharedPreferences
) : LiveData<String>() {
    private val prefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == this@StringPreferenceLiveData.key) {
                sharedPreferences?.getString(key, defaultValue)?.let {
                    value = it
                }
            }
        }

    override fun onActive() {
        sharedPreferences.registerOnSharedPreferenceChangeListener(prefListener)
        value = sharedPreferences.getString(key, defaultValue)
    }

    override fun onInactive() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(prefListener)
    }
}

/** Exposes a string in [SharedPreferences] as [LiveData] */
class FloatPreferenceLiveData(
    private val key: String,
    private val defaultValue: Float,
    private val sharedPreferences: SharedPreferences
) : LiveData<Float>() {
    private val prefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == this@FloatPreferenceLiveData.key) {
                sharedPreferences?.getFloat(key, defaultValue)?.let {
                    value = it
                }
            }
        }

    override fun onActive() {
        sharedPreferences.registerOnSharedPreferenceChangeListener(prefListener)
        value = sharedPreferences.getFloat(key, defaultValue)
    }

    override fun onInactive() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(prefListener)
    }
}
