package io.github.mattpvaughn.chronicle.features.settings

data class PreferenceModel(
    val type: PreferenceType,
    val title: String,
    val key: String = "",
    val explanation: String = "",
    val click: PreferenceClick = object : PreferenceClick {
        override fun onClick() {
            // Do nothing by default
        }
    }
) {
    fun hasExplanation(): Boolean {
        return explanation.isNotEmpty()
    }
}

interface PreferenceClick {
    fun onClick()
}

enum class PreferenceType { TITLE, CLICKABLE, BOOLEAN, INTEGER, FLOAT }

val prefIntMap = mapOf(PreferenceType.TITLE to 1,
                       PreferenceType.CLICKABLE to 2,
                       PreferenceType.BOOLEAN to 3,
                       PreferenceType.INTEGER to 4,
                       PreferenceType.FLOAT to 5)
