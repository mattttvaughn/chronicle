package io.github.mattpvaughn.chronicle.features.bookdetails

import android.graphics.PorterDuff
import android.util.Log
import android.widget.ImageView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME

@BindingAdapter("playbackSpeed")
fun bindPlaybackSpeed(imageView: ImageView, speed: Float) {
    Log.i(APP_NAME, "Playback speed changed! $speed")
    when (speed) {
        0.5f -> imageView.setImageResource(R.drawable.ic_speed_up_0_5x)
        0.7f -> imageView.setImageResource(R.drawable.ic_speed_up_0_7x)
        1.0f -> imageView.setImageResource(R.drawable.ic_speed_up_1_0x)
        1.2f -> imageView.setImageResource(R.drawable.ic_speed_up_1_2x)
        1.5f -> imageView.setImageResource(R.drawable.ic_speed_up_1_5x)
        2.0f -> imageView.setImageResource(R.drawable.ic_speed_up_2_0x)
        3.0f -> imageView.setImageResource(R.drawable.ic_speed_up_3_0x)
        else -> throw Error("Illegal playback speed set: $speed")
    }
}

@BindingAdapter("trackList")
fun bindTrackList(recyclerView: RecyclerView, tracks: List<MediaItemTrack>?) {
    val adapter = recyclerView.adapter as TrackListAdapter
    adapter.submitList(tracks ?: ArrayList())
}

@BindingAdapter("android:src")
fun bindImageDrawableSource(imageView: ImageView, @DrawableRes drawableRes: Int) {
    imageView.setImageResource(drawableRes)
}

@BindingAdapter("tintRes")
fun bindTintResource(imageView: ImageView, @ColorRes colorRes: Int) {
    imageView.setColorFilter(
        ContextCompat.getColor(imageView.context, colorRes),
        PorterDuff.Mode.SRC_IN
    )
}
