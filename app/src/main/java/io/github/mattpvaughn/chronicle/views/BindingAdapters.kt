package io.github.mattpvaughn.chronicle.views

import android.app.Activity
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.databinding.BindingAdapter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.Headers
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.data.sources.plex.PLACEHOLDER_URL
import timber.log.Timber
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

    val imageSize = imageView.resources.getDimension(R.dimen.audiobook_image_width).toInt()
    val config = Injector.get().plexConfig()
    val url =
        URL(config.toServerString("photo/:/transcode?width=$imageSize&height=$imageSize&url=$src"))
    val glideUrl = GlideUrlRelativeCacheKey(url, Injector.get().plexConfig().makeGlideHeaders())

    // If no server is connected, don't bother fetching from server, just check cache
    val onlyRetrieveFromCache =
        src.isNullOrBlank() || !serverConnected || config.url == PLACEHOLDER_URL

    Glide.with(imageView)
        .load(glideUrl)
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .placeholder(R.drawable.book_cover_missing_placeholder)
        .error(R.drawable.book_cover_missing_placeholder)
        .transition(DrawableTransitionOptions.withCrossFade())
        .transform(CenterCrop())
        .onlyRetrieveFromCache(onlyRetrieveFromCache)
        .listener(object : RequestListener<Drawable> {
            // As of March 16, 2021, including this listener fixes issue of some thumbs not loading
            override fun onLoadFailed(
                e: GlideException?,
                model: Any?,
                target: Target<Drawable>?,
                isFirstResource: Boolean
            ): Boolean {
                Timber.i("Load thumbnail failure: retrieveFromCache=$onlyRetrieveFromCache, url=$url: $e")
                return false
            }

            override fun onResourceReady(
                resource: Drawable?,
                model: Any?,
                target: Target<Drawable>?,
                dataSource: DataSource?,
                isFirstResource: Boolean
            ): Boolean {
                Timber.i("Load thumbnail success: retrieveFromCache=$onlyRetrieveFromCache, url=$url")
                return false
            }

        })
        .into(imageView)
}


/**
 * A [GlideUrl] which uses the queries in the URL as the key, as opposed to the entire URL, so that
 * caching will work regardless of the route connecting the user to the server
 */
class GlideUrlRelativeCacheKey(url: URL?, headers: Headers?) : GlideUrl(url, headers) {
    override fun getCacheKey(): String {
        val url = toStringUrl()
        val query = Uri.parse(url).query
        return query ?: ""
    }
}


// NOTE: this will not work for Android versions HoneyComb and below, and DataBinding overrides the
// tag set on all outermost layouts in a data bound layout xml
@RequiresApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
@BindingAdapter("specialTag")
fun bindTag(view: View, o: Any) {
    view.tag = o
}
