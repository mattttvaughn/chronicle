package io.github.mattpvaughn.chronicle.features.chooselibrary

import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME

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
fun bindRecyclerView(recyclerView: RecyclerView, data: List<LibraryModel>?) {
    val adapter = recyclerView.adapter as LibraryListAdapter
    adapter.submitList(data)
}
