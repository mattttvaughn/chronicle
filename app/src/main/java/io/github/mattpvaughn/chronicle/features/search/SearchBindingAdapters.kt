package io.github.mattpvaughn.chronicle.features.search

import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.features.library.AudiobookSearchAdapter


@BindingAdapter("serverConnectedSearch")
fun bindSearchRecyclerView(recyclerView: RecyclerView, serverConnected: Boolean) {
    val adapter = recyclerView.adapter as AudiobookSearchAdapter
    adapter.setServerConnected(serverConnected)
}

@BindingAdapter("searchBookList")
fun bindSearchRecyclerView(recyclerView: RecyclerView, data: List<Audiobook>?) {
    val adapter = recyclerView.adapter as AudiobookSearchAdapter
    adapter.submitList(data)
}

