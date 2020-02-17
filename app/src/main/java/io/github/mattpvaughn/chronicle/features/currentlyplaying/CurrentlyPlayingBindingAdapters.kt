package io.github.mattpvaughn.chronicle.features.currentlyplaying

import android.transition.TransitionManager
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.constraintlayout.widget.ConstraintSet.*
import androidx.databinding.BindingAdapter
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.transition.AutoTransition
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.application.MainActivityViewModel
import io.github.mattpvaughn.chronicle.application.MainActivityViewModel.BottomSheetState.*


@BindingAdapter("bottomSheetState")
fun setBottomSheetState(parent: ConstraintLayout, state: MainActivityViewModel.BottomSheetState) {
    val constraints = ConstraintSet()
    constraints.clone(parent)
    when (state) {
        EXPANDED -> expandConstraint(constraints)
        COLLAPSED -> collapseConstraint(constraints)
        HIDDEN -> hideConstraint(constraints)
    }

    val transition = AutoTransition()
    transition.interpolator = FastOutSlowInInterpolator()
    transition.duration = parent.context.resources.getInteger(R.integer.short_animation_ms).toLong()
    TransitionManager.beginDelayedTransition(parent)
    parent.setConstraintSet(constraints)
    constraints.applyTo(parent)

    val bottomSheetHandle = parent.findViewById<View>(R.id.currently_playing_handle)
    bottomSheetHandle.visibility = if (state == COLLAPSED) View.VISIBLE else View.GONE
}

private fun collapseConstraint(constraintSet: ConstraintSet) {
    constraintSet.connect(
        R.id.currently_playing_container,
        TOP,
        R.id.currently_playing_collapsed_top,
        BOTTOM
    )
    constraintSet.connect(R.id.currently_playing_container, BOTTOM, R.id.bottom_nav, TOP)
}

private fun expandConstraint(constraintSet: ConstraintSet) {
    constraintSet.connect(R.id.currently_playing_container, TOP, PARENT_ID, TOP)
    constraintSet.connect(R.id.currently_playing_container, BOTTOM, R.id.bottom_nav, TOP)
}

private fun hideConstraint(constraintSet: ConstraintSet) {
    constraintSet.connect(R.id.currently_playing_container, TOP, R.id.bottom_nav, TOP)
    constraintSet.connect(R.id.currently_playing_container, BOTTOM, R.id.bottom_nav, TOP)
}