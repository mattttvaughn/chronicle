package io.github.mattpvaughn.chronicle.views

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.databinding.BindingAdapter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.Headers
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.application.Injector
import java.net.URL


@BindingAdapter(value = ["srcRounded", "serverConnected"], requireAll = true)
fun bindImageRounded(
    imageView: ImageView,
    src: String?,
    serverConnected: Boolean
) {
    if ((imageView.context as Activity).isDestroyed) {
        return
    }
    if (src.isNullOrEmpty()) {
        imageView.setImageResource(R.drawable.book_cover_missing_placeholder)
        return
    }

    val imageSize = imageView.resources.getDimension(R.dimen.audiobook_image_width).toInt()
    val config = Injector.get().plexConfig()
    val url =
        URL(config.toServerString("photo/:/transcode?width=$imageSize&height=$imageSize&url=$src"))
    val glideUrl = GlideUrlRelativeCacheKey(url, Injector.get().plexConfig().makeGlideHeaders())

    Glide.with(imageView)
        .load(glideUrl)
        .placeholder(R.drawable.book_cover_missing_placeholder)
        .transition(DrawableTransitionOptions.withCrossFade())
        .transform(CenterCrop())
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .into(imageView)
}


/**
 * A [GlideUrl] which uses the queries in the URL as the key, as opposed to the entire URL, so that
 * caching will work regardless of the server the user is connected to
 */
class GlideUrlRelativeCacheKey(url: URL?, headers: Headers?) : GlideUrl(url, headers) {
    override fun getCacheKey(): String {
        val url = toStringUrl()
        val query = Uri.parse(url).query
        return query ?: url
    }
}


// NOTE: this will not work for Android versions HoneyComb and below, and DataBinding overrides the
// tag set on all outermost layouts in a data bound layout xml
@RequiresApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
@BindingAdapter("specialTag")
fun bindTag(view: View, o: Any) {
    view.tag = o
}
