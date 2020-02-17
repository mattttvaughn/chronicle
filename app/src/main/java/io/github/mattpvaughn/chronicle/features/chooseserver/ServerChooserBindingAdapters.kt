package io.github.mattpvaughn.chronicle.features.chooseserver

import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME

@BindingAdapter("loadingStatus")
fun bindLoadingStatus(recyclerView: RecyclerView, loadingStatus: ChooseServerViewModel.LoadingStatus?) {
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
fun bindLoadingStatus(progressBar: ProgressBar, loadingStatus: ChooseServerViewModel.LoadingStatus?) {
    when (loadingStatus) {
        ChooseServerViewModel.LoadingStatus.ERROR -> progressBar.visibility = View.GONE
        ChooseServerViewModel.LoadingStatus.DONE -> progressBar.visibility = View.GONE
        ChooseServerViewModel.LoadingStatus.LOADING -> progressBar.visibility = View.VISIBLE
    }
}

@BindingAdapter("listData")
fun bindRecyclerView(recyclerView: RecyclerView, data: List<ServerModel>?) {
    val adapter = recyclerView.adapter as ServerListAdapter
    adapter.submitList(data)
}
