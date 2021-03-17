package io.github.mattpvaughn.chronicle.views

import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import timber.log.Timber

fun ChipGroup.checkRadioButtonWithTag(o: Any) {
    for (x in 0 until childCount) {
        val tempChild = getChildAt(x) as Chip
        Timber.i("Child tag is ${tempChild.tag}")
        if (tempChild.tag == o) {
            check(tempChild.id)
        }
    }
}

