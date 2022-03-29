package io.github.mattpvaughn.chronicle.features.bookdetails

import android.content.res.Resources.NotFoundException
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
import okhttp3.internal.toHexString
import timber.log.Timber

@BindingAdapter("playbackSpeed")
fun bindPlaybackSpeed(imageView: ImageView, speed: Float) {
    Timber.i("Playback speed changed! $speed")
    imageView.contentDescription = imageView.resources.getString(
        ResourceString(R.string.playback_speed_desc, listOf(speed.toString()))
    )
    when (speed) {
        0.5f -> imageView.setImageResource(R.drawable.ic_speed_up_0_5x)
        0.6f -> imageView.setImageResource(R.drawable.ic_speed_up_0_6x)
        0.7f -> imageView.setImageResource(R.drawable.ic_speed_up_0_7x)
        0.8f -> imageView.setImageResource(R.drawable.ic_speed_up_0_8x)
        0.9f -> imageView.setImageResource(R.drawable.ic_speed_up_0_9x)
        1.0f -> imageView.setImageResource(R.drawable.ic_speed_up_1_0x)
        1.1f -> imageView.setImageResource(R.drawable.ic_speed_up_1_1x)
        1.2f -> imageView.setImageResource(R.drawable.ic_speed_up_1_2x)
        1.3f -> imageView.setImageResource(R.drawable.ic_speed_up_1_3x)
        1.4f -> imageView.setImageResource(R.drawable.ic_speed_up_1_4x)
        1.5f -> imageView.setImageResource(R.drawable.ic_speed_up_1_5x)
        1.6f -> imageView.setImageResource(R.drawable.ic_speed_up_1_6x)
        1.7f -> imageView.setImageResource(R.drawable.ic_speed_up_1_7x)
        1.8f -> imageView.setImageResource(R.drawable.ic_speed_up_1_8x)
        1.9f -> imageView.setImageResource(R.drawable.ic_speed_up_1_9x)
        2.0f -> imageView.setImageResource(R.drawable.ic_speed_up_2_0x)
        2.5f -> imageView.setImageResource(R.drawable.ic_speed_up_2_5x)
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

@BindingAdapter("app:tint")
fun bindTintResource(imageView: ImageView, @ColorRes colorRes: Int) {
    if (colorRes != 0) {
        try {
            imageView.setColorFilter(
                ContextCompat.getColor(imageView.context, colorRes),
                PorterDuff.Mode.SRC_IN
            )
        } catch (rnf: NotFoundException) {
            Timber.e("Could not bind tint with res: 0x${colorRes.toHexString()}")
        }
    }
}
