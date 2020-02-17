package io.github.mattpvaughn.chronicle.features.library

import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.features.bookdetails.TrackListAdapter


@BindingAdapter("bookList")
fun bindRecyclerView(recyclerView: RecyclerView, data: List<Audiobook>?) {
    val adapter = recyclerView.adapter as AudiobookAdapter
    adapter.submitList(data)
}

@BindingAdapter("serverConnected")
fun bindRecyclerView(recyclerView: RecyclerView, serverConnected: Boolean) {
    val adapter = recyclerView.adapter as AudiobookAdapter
    adapter.setServerConntected(serverConnected)
}

@BindingAdapter("overrideWidth")
fun overrideWidth(view: View, width: Float) {
    view.layoutParams.width = if (width > 0) width.toInt() else MATCH_PARENT
}

@BindingAdapter("activeTrackId")
fun setActiveTrack(recyclerView: RecyclerView, trackId: Int) {
    val adapter = recyclerView.adapter as TrackListAdapter
    adapter.setActiveTrack(trackId)
}

