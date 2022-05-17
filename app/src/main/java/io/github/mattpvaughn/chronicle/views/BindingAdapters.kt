package io.github.mattpvaughn.chronicle.views

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import androidx.databinding.BindingAdapter
import com.facebook.cache.common.CacheKey
import com.facebook.drawee.view.SimpleDraweeView
import com.facebook.imagepipeline.request.ImageRequest
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.application.Injector
import timber.log.Timber

@BindingAdapter(value = ["srcRounded", "serverConnected"], requireAll = true)
fun bindImageRounded(
    draweeView: SimpleDraweeView,
    src: String?,
    serverConnected: Boolean
) {
    if ((draweeView.context as Activity).isDestroyed) {
        return
    }

    val imageSize =
        draweeView.resources.getDimension(R.dimen.currently_playing_artwork_max_size).toInt()
    val config = Injector.get().plexConfig()
    val url = config.toServerString("photo/:/transcode?width=$imageSize&height=$imageSize&url=$src")
        .toUri()

    // If no server is connected, don't bother fetching from server, just check cache
    val request = ImageRequest.fromUri(url)
    draweeView.setImageRequest(request)
}

/**
 * A [CacheKey] which uses the query (everything after ?) in the URL as the key,
 * as opposed to the entire URL, so that caching will work regardless of the route
 * connecting the user to the server
 */
class UrlQueryCacheKey(private val url: Uri?) : CacheKey {

    override fun containsUri(uri: Uri?): Boolean {
        Timber.i("Checking cache for image")
        return uri?.query?.contains(url?.query ?: "") ?: false
    }

    // Seems to be primarily used for debugging
    override fun getUriString() = url?.query ?: ""

    override fun isResourceIdForDebugging() = false

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UrlQueryCacheKey

        val isEquals = url?.query == other.url?.query
        Timber.i("Checking for equality: ${this.url?.query}, ${other.url?.query}, $isEquals")

        return isEquals
    }

    override fun hashCode(): Int {
        return url?.query?.hashCode() ?: 0
    }

    override fun toString(): String {
        return url?.query.toString()
    }
}

// NOTE: this will not work for Android versions HoneyComb and below, and DataBinding overrides the
// tag set on all outermost layouts in a data bound layout xml
@RequiresApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
@BindingAdapter("specialTag")
fun bindTag(view: View, o: Any) {
    view.tag = o
}
