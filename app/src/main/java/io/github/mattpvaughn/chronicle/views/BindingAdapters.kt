package io.github.mattpvaughn.chronicle.views

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.databinding.BindingAdapter
import coil.Coil
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.data.sources.HttpMediaSource
import io.github.mattpvaughn.chronicle.data.sources.MediaSource


@BindingAdapter(value = ["srcRounded", "serverConnected", "mediaSource"], requireAll = true)
fun bindImageRounded(
    imageView: ImageView,
    srcRounded: String?,
    serverConnected: Boolean,
    mediaSource: MediaSource?
) {
    if ((imageView.context as Activity).isDestroyed || srcRounded.isNullOrEmpty() || mediaSource == null) {
        return
    }

    val url = if (mediaSource is HttpMediaSource) {
        Uri.parse(mediaSource.makeThumbUri(srcRounded).toString())
    } else {
        mediaSource.makeThumbUri(srcRounded)
    }

    val request = mediaSource.getThumbBuilder()
        .crossfade(true)
        .placeholder(R.drawable.book_cover_missing_placeholder)
        .error(R.drawable.book_cover_missing_placeholder)
        .target(imageView)
        .build()

    Coil.imageLoader(imageView.context).enqueue(request)

}

// NOTE: this will not work for Android versions HoneyComb and below, and DataBinding overrides the
// tag set on all outermost layouts in a data bound layout xml
@RequiresApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
@BindingAdapter("specialTag")
fun bindTag(view: View, o: Any) {
    view.tag = o
}
