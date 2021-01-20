package io.github.mattpvaughn.chronicle.features.bookdetails

import android.graphics.PorterDuff
import android.widget.ImageView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.data.model.Chapter
import io.github.mattpvaughn.chronicle.views.BottomSheetChooser.FormattableString.ResourceString
import io.github.mattpvaughn.chronicle.views.getString
import timber.log.Timber

@BindingAdapter("playbackSpeed")
fun bindPlaybackSpeed(imageView: ImageView, speed: Float) {
    Timber.i("Playback speed changed! $speed")
    imageView.contentDescription = imageView.resources.getString(
        ResourceString(R.string.playback_speed_desc, listOf(speed.toString()))
    )
    when (speed) {
        0.5f -> imageView.setImageResource(R.drawable.ic_speed_up_0_5x)
        0.7f -> imageView.setImageResource(R.drawable.ic_speed_up_0_7x)
        1.0f -> imageView.setImageResource(R.drawable.ic_speed_up_1_0x)
        1.2f -> imageView.setImageResource(R.drawable.ic_speed_up_1_2x)
        1.5f -> imageView.setImageResource(R.drawable.ic_speed_up_1_5x)
        1.7f -> imageView.setImageResource(R.drawable.ic_speed_up_1_7x)
        2.0f -> imageView.setImageResource(R.drawable.ic_speed_up_2_0x)
        3.0f -> imageView.setImageResource(R.drawable.ic_speed_up_3_0x)
        else -> throw Error("Illegal playback speed set: $speed")
    }
}

@BindingAdapter("chapterList")
fun bindChapterList(recyclerView: RecyclerView, chapters: List<Chapter>?) {
    val adapter = recyclerView.adapter as ChapterListAdapter
    adapter.submitChapters(chapters ?: emptyList())
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
