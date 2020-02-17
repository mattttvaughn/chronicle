package io.github.mattpvaughn.chronicle.views

import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.RecyclerView

@BindingAdapter("options")
fun setOptions(bottomSheetChooser: BottomSheetChooser, options: List<String>) {
    bottomSheetChooser.setOptions(options)
}

@BindingAdapter("optionsListener")
fun setOptionsListener(bottomSheetChooser: BottomSheetChooser, listener: BottomSheetChooser.ItemSelectedListener) {
    bottomSheetChooser.setOptionsSelectedListener(listener)
}

@BindingAdapter("show")
fun setShow(bottomSheetChooser: BottomSheetChooser, show: Boolean) {
    if (show) {
        bottomSheetChooser.show()
    } else {
        bottomSheetChooser.hide()
    }
}

@BindingAdapter("bottomSheetTitle")
fun setBottomSheetTitle(bottomSheetChooser: BottomSheetChooser, title: String) {
    bottomSheetChooser.setTitle(title)
}

@BindingAdapter("options")
fun setOptions(recyclerView: RecyclerView, options: List<String>) {
    val adapter = recyclerView.adapter as BottomSheetChooser.OptionsListAdapter
    adapter.submitList(options)
}

@BindingAdapter("listener")
fun setOptionsListener(recyclerView: RecyclerView, listener: BottomSheetChooser.ItemSelectedListener) {
    val adapter = recyclerView.adapter as BottomSheetChooser.OptionsListAdapter
    adapter.setListener(listener)
}
