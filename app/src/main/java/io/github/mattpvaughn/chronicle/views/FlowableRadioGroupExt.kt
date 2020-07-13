package io.github.mattpvaughn.chronicle.views

import android.widget.RadioButton
import timber.log.Timber

fun FlowableRadioGroup.checkRadioButtonWithTag(o: Any) {
    for (x in 0 until childCount) {
        val tempChild = getChildAt(x) as RadioButton
        Timber.i("Child tag is ${tempChild.tag}")
        if (tempChild.tag == o) {
            check(tempChild.id)
        }
    }
}

