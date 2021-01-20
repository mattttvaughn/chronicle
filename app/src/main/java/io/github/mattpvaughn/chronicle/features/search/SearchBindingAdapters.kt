package io.github.mattpvaughn.chronicle.features.search

import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.features.library.AudiobookSearchAdapter


@BindingAdapter("sourceConnectionsSearch")
fun bindSearchRecyclerViewConnections(recyclerView: RecyclerView, connectedSourceIds: List<Long>) {
    val adapter = recyclerView.adapter as AudiobookSearchAdapter
    adapter.setActiveConnections(connectedSourceIds)
}

@BindingAdapter("searchBookList")
fun bindSearchRecyclerView(recyclerView: RecyclerView, data: List<Audiobook>?) {
    val adapter = recyclerView.adapter as AudiobookSearchAdapter
    adapter.submitList(data)
}

