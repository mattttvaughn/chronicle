package io.github.mattpvaughn.chronicle.features.login

import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.mattpvaughn.chronicle.data.model.LoadingStatus
import io.github.mattpvaughn.chronicle.data.model.PlexLibrary
import io.github.mattpvaughn.chronicle.data.model.ServerModel
import io.github.mattpvaughn.chronicle.data.sources.plex.model.PlexUser
import timber.log.Timber

@BindingAdapter("loadingStatus")
fun bindLoadingStatus(
    recyclerView: RecyclerView,
    loadingStatus: LoadingStatus?
) {
    Timber.i("Loading status: $loadingStatus")
    when (loadingStatus) {
        LoadingStatus.ERROR -> recyclerView.visibility = View.GONE
        LoadingStatus.DONE -> recyclerView.visibility = View.VISIBLE
        LoadingStatus.LOADING -> recyclerView.visibility = View.GONE
        else -> {}
    }
}

@BindingAdapter("loadingStatus")
fun bindLoadingStatus(errorView: TextView, loadingStatus: LoadingStatus?) {
    when (loadingStatus) {
        LoadingStatus.ERROR -> errorView.visibility = View.VISIBLE
        LoadingStatus.DONE -> errorView.visibility = View.GONE
        LoadingStatus.LOADING -> errorView.visibility = View.GONE
        else -> {}
    }
}

@BindingAdapter("loadingStatus")
fun bindLoadingStatus(
    progressBar: ProgressBar,
    loadingStatus: LoadingStatus?
) {
    when (loadingStatus) {
        LoadingStatus.ERROR -> progressBar.visibility = View.GONE
        LoadingStatus.DONE -> progressBar.visibility = View.GONE
        LoadingStatus.LOADING -> progressBar.visibility = View.VISIBLE
        else -> {}
    }
}

@BindingAdapter("listData")
fun bindServerData(recyclerView: RecyclerView, data: List<ServerModel>) {
    val adapter = recyclerView.adapter as ServerListAdapter
    adapter.submitList(data)
}

@BindingAdapter("users")
fun bindUsers(recyclerView: RecyclerView, data: List<PlexUser>) {
    val adapter = recyclerView.adapter as UserListAdapter
    adapter.submitList(data)
}


@BindingAdapter("listData")
fun bindLibraryData(recyclerView: RecyclerView, data: List<PlexLibrary>) {
    val adapter = recyclerView.adapter as LibraryListAdapter
    adapter.submitList(data)
}
