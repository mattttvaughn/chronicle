package io.github.mattpvaughn.chronicle.features.login

import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.mattpvaughn.chronicle.data.model.Library
import io.github.mattpvaughn.chronicle.data.model.ServerModel
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME

@BindingAdapter("loadingStatus")
fun bindLoadingStatus(
    recyclerView: RecyclerView,
    loadingStatus: ChooseServerViewModel.LoadingStatus?
) {
    Log.i(APP_NAME, "Loading status: $loadingStatus")
    when (loadingStatus) {
        ChooseServerViewModel.LoadingStatus.ERROR -> recyclerView.visibility = View.GONE
        ChooseServerViewModel.LoadingStatus.DONE -> recyclerView.visibility = View.VISIBLE
        ChooseServerViewModel.LoadingStatus.LOADING -> recyclerView.visibility = View.GONE
    }
}

@BindingAdapter("loadingStatus")
fun bindLoadingStatus(errorView: TextView, loadingStatus: ChooseServerViewModel.LoadingStatus?) {
    when (loadingStatus) {
        ChooseServerViewModel.LoadingStatus.ERROR -> errorView.visibility = View.VISIBLE
        ChooseServerViewModel.LoadingStatus.DONE -> errorView.visibility = View.GONE
        ChooseServerViewModel.LoadingStatus.LOADING -> errorView.visibility = View.GONE
    }
}

@BindingAdapter("loadingStatus")
fun bindLoadingStatus(
    progressBar: ProgressBar,
    loadingStatus: ChooseServerViewModel.LoadingStatus?
) {
    when (loadingStatus) {
        ChooseServerViewModel.LoadingStatus.ERROR -> progressBar.visibility = View.GONE
        ChooseServerViewModel.LoadingStatus.DONE -> progressBar.visibility = View.GONE
        ChooseServerViewModel.LoadingStatus.LOADING -> progressBar.visibility = View.VISIBLE
    }
}

@BindingAdapter("listData")
fun bindServerData(recyclerView: RecyclerView, data: List<ServerModel>) {
    val adapter = recyclerView.adapter as ServerListAdapter
    adapter.submitList(data)
}

@BindingAdapter("loadingStatus")
fun bindLoadingStatus(
    recyclerView: RecyclerView,
    loadingStatus: ChooseLibraryViewModel.LoadingStatus?
) {
    Log.i(APP_NAME, "Loading status: $loadingStatus")
    when (loadingStatus) {
        ChooseLibraryViewModel.LoadingStatus.ERROR -> recyclerView.visibility = View.GONE
        ChooseLibraryViewModel.LoadingStatus.DONE -> recyclerView.visibility = View.VISIBLE
        ChooseLibraryViewModel.LoadingStatus.LOADING -> recyclerView.visibility = View.GONE
    }
}

@BindingAdapter("loadingStatus")
fun bindLoadingStatus(errorView: TextView, loadingStatus: ChooseLibraryViewModel.LoadingStatus?) {
    when (loadingStatus) {
        ChooseLibraryViewModel.LoadingStatus.ERROR -> errorView.visibility = View.VISIBLE
        ChooseLibraryViewModel.LoadingStatus.DONE -> errorView.visibility = View.GONE
        ChooseLibraryViewModel.LoadingStatus.LOADING -> errorView.visibility = View.GONE
    }
}

@BindingAdapter("loadingStatus")
fun bindLoadingStatus(
    progressBar: ProgressBar,
    loadingStatus: ChooseLibraryViewModel.LoadingStatus?
) {
    when (loadingStatus) {
        ChooseLibraryViewModel.LoadingStatus.ERROR -> progressBar.visibility = View.GONE
        ChooseLibraryViewModel.LoadingStatus.DONE -> progressBar.visibility = View.GONE
        ChooseLibraryViewModel.LoadingStatus.LOADING -> progressBar.visibility = View.VISIBLE
    }
}

@BindingAdapter("listData")
fun bindLibraryData(recyclerView: RecyclerView, data: List<Library>) {
    val adapter = recyclerView.adapter as LibraryListAdapter
    adapter.submitList(data)
}
