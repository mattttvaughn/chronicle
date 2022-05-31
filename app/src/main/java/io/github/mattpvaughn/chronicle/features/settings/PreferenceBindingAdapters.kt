package io.github.mattpvaughn.chronicle.features.settings

import androidx.databinding.BindingAdapter

@BindingAdapter("preferences")
fun setPreferencesForList(settingsList: SettingsList, prefs: List<PreferenceModel>) {
    settingsList.setPreferences(prefs)
}
