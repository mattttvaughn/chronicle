package io.github.mattpvaughn.chronicle.views

import android.view.View
import android.widget.TextView
import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.views.BottomSheetChooser.BottomChooserState
import io.github.mattpvaughn.chronicle.views.BottomSheetChooser.FormattableString

@BindingAdapter("bottomChooserState")
fun setBottomChooserState(
    bottomSheetChooser: BottomSheetChooser,
    state: BottomChooserState
) {
    bottomSheetChooser.setTitle(state.title)
    bottomSheetChooser.setOptionsSelectedListener(state.listener)
    if (state.shouldShow) {
        // Don't run showing animation if it's already showing
        if (bottomSheetChooser.findViewById<View>(R.id.tap_to_close).visibility != View.VISIBLE) {
            bottomSheetChooser.show()
        }
    } else {
        bottomSheetChooser.hide(false)
    }
    bottomSheetChooser.setOptions(state.options)
}

@BindingAdapter("android:text")
fun setFormattableText(textView: TextView, formattableString: FormattableString) {
    textView.text = formattableString.format(textView.context.resources)
}

@BindingAdapter("options")
fun setOptions(recyclerView: RecyclerView, options: List<FormattableString>) {
    val adapter = recyclerView.adapter as BottomSheetChooser.OptionsListAdapter
    adapter.submitList(options)
}

@BindingAdapter("listener")
fun setOptionsListener(
    recyclerView: RecyclerView,
    listener: BottomSheetChooser.BottomChooserListener
) {
    val adapter = recyclerView.adapter as BottomSheetChooser.OptionsListAdapter
    adapter.setListener(listener)
    // force a rebind of listeners- this would be bad for performance if it were a big list, but
    // now it's just bad because it's hacky and inelegant and showcases my poor knowledge of
    // closures and scoping and stuff
    adapter.notifyDataSetChanged()
}
