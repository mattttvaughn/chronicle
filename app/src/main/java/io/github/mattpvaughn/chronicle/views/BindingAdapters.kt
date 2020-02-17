package io.github.mattpvaughn.chronicle.views

import android.app.Activity
import android.net.Uri
import android.widget.ImageView
import androidx.databinding.BindingAdapter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.Headers
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.signature.ObjectKey
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.data.plex.PlexRequestSingleton
import java.net.URL


@BindingAdapter(value = ["srcRounded", "lastUpdated", "serverConnected"], requireAll = true)
fun bindImageRounded(
    imageView: ImageView,
    src: String?,
    lastUpdated: Long,
    serverConnected: Boolean
) {
    if (src.isNullOrEmpty() || (imageView.context as Activity).isDestroyed) {
        return
    }
    val radiusPx = imageView.resources.getDimension(R.dimen.audiobook_item_radius).toInt()
    val imageSize = imageView.resources.getDimension(R.dimen.audiobook_image_height).toInt()
    val url =
        URL(PlexRequestSingleton.toServerString("photo/:/transcode?width=$imageSize&height=$imageSize&url=$src"))
    val glideUrl = GlideUrlRelativeCacheKey(
        url,
        PlexRequestSingleton.makeGlideHeaders()
    )

    Glide.with(imageView)
        .load(glideUrl)
        .centerCrop()
        .placeholder(R.drawable.ic_image_white)
        .transition(DrawableTransitionOptions.withCrossFade())
        .transform(RoundedCorners(radiusPx))
        .error(R.drawable.ic_broken_image)
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .signature(ObjectKey(lastUpdated))
        .into(imageView)
}

class GlideUrlRelativeCacheKey(url: URL?, headers: Headers?) : GlideUrl(url, headers) {
    override fun getCacheKey(): String {
        val url = toStringUrl()
        val query = Uri.parse(url).query
        return query ?: url
    }
}
