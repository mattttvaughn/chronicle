package io.github.mattpvaughn.chronicle.views

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Context.LAYOUT_INFLATER_SERVICE
import android.content.res.Resources
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.StringRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.databinding.ViewBottomSheetChooserBinding
import io.github.mattpvaughn.chronicle.databinding.ViewBottomSheetChooserItemBinding

/**
 * A [bottom sheet](https://material.io/develop/android/components/bottom-sheet-behavior/) which can
 * be used to show a list of strings along with accompanying listeners to handle their being clicked.
 *
 * Note: while this could be juiced up with keys/values for each string, different item types, etc.,
 * I'm going to put a higher value on the simple interface and current flexibility. Subject to change
 * in the future, but think about it first.
 */
class BottomSheetChooser : FrameLayout {

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    )

    fun setOptions(options: List<FormattableString>) {
        val binding = findViewById<View>(R.id.bottom_sheet).tag as ViewBottomSheetChooserBinding
        binding.options = options
        binding.executePendingBindings()
    }

    fun setTitle(newTitle: FormattableString) {
        val binding = findViewById<View>(R.id.bottom_sheet).tag as ViewBottomSheetChooserBinding
        binding.title = newTitle
        binding.executePendingBindings()
    }

    var listener = BottomChooserListener.emptyListener

    fun setOptionsSelectedListener(listener: BottomChooserListener) {
        val binding = findViewById<View>(R.id.bottom_sheet).tag as ViewBottomSheetChooserBinding
        this.listener = listener
        binding.listener = listener
        binding.executePendingBindings()
    }

    /**
     * A [BottomChooserListener], but only handles item clicks
     */
    abstract class BottomChooserItemListener : BottomChooserListener {
        abstract override fun onItemClicked(formattableString: FormattableString)
        override fun onChooserClosed(wasBackgroundClicked: Boolean) {}
    }

    interface BottomChooserListener {
        /** Triggers when an item in the chooser is clicked */
        fun onItemClicked(formattableString: FormattableString)

        /**
         * Triggers when the chooser is closed. Passes a boolean, [wasBackgroundClicked] indicating
         * whether the chooser was closed as the result of an item being selected, or whether it
         * was closed as a result of [ViewBottomSheetChooserBinding.tapToClose] being clicked
         */
        fun onChooserClosed(wasBackgroundClicked: Boolean = false)

        companion object {
            val emptyListener = object : BottomChooserListener {
                override fun onItemClicked(formattableString: FormattableString) {}
                override fun onChooserClosed(wasBackgroundClicked: Boolean) {}
            }
        }
    }

    private val optionsAdapter = OptionsListAdapter(BottomChooserListener.emptyListener)

    fun hide(wasBackgroundClicked: Boolean) {
        listener.onChooserClosed(wasBackgroundClicked)
        val binding = findViewById<View>(R.id.bottom_sheet).tag as ViewBottomSheetChooserBinding
        val animDuration = context.resources.getInteger(R.integer.short_animation_ms).toLong()
        // If the height has not been determined yet, don't animate
        if (measuredHeight != 0) {
            ObjectAnimator.ofFloat(
                binding.bottomSheetContainer,
                "translationY",
                measuredHeight.toFloat()
            )
                .apply {
                    duration = animDuration
                    start()
                }
            ObjectAnimator.ofFloat(binding.tapToClose, "alpha", 0F).apply {
                duration = animDuration
                start()
                postDelayed({
                    binding.tapToClose.visibility = View.GONE
                }, animDuration)
            }
        }
    }

    fun show() {
        val binding = findViewById<View>(R.id.bottom_sheet).tag as ViewBottomSheetChooserBinding
        binding.bottomSheetContainer.visibility = View.VISIBLE
        binding.bottomSheetContainer.translationY = measuredHeight.toFloat()
        binding.tapToClose.visibility = View.VISIBLE
        ObjectAnimator.ofFloat(binding.bottomSheetContainer, "translationY", 0F).apply {
            duration = context.resources.getInteger(R.integer.short_animation_ms).toLong()
            start()
        }
        ObjectAnimator.ofFloat(binding.tapToClose, "alpha", 1F).apply {
            duration = context.resources.getInteger(R.integer.short_animation_ms).toLong()
            start()
        }
    }

    init {
        // Always expand to size of parent so the click-to-close dummy view will be expanded
//        layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
        val inflater = context.getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val binding = ViewBottomSheetChooserBinding.inflate(inflater, this, false)
        addView(binding.root)
        binding.root.tag = binding
        binding.options = emptyList()
        binding.title = FormattableString.LiteralString("Title")
        binding.listener = BottomChooserListener.emptyListener
        binding.tapToClose.setOnClickListener {
            hide(wasBackgroundClicked = true)
        }
        binding.bottomSheetOptions.adapter = optionsAdapter
        binding.executePendingBindings()
    }

    class OptionsListAdapter(private var listener: BottomChooserListener) :
        ListAdapter<FormattableString, OptionsListAdapter.OptionViewHolder>(
            FormattableStringDiffUtilCallback()
        ) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OptionViewHolder {
            return OptionViewHolder.from(parent)
        }

        fun setListener(_listener: BottomChooserListener) {
            listener = _listener
        }

        override fun onBindViewHolder(holder: OptionViewHolder, position: Int) {
            holder.bind(getItem(position), listener)
        }

        class OptionViewHolder(val binding: ViewBottomSheetChooserItemBinding) :
            RecyclerView.ViewHolder(binding.root) {
            fun bind(option: FormattableString, listener: BottomChooserListener) {
                binding.option = option
                binding.listener = listener
                binding.executePendingBindings()
            }

            companion object {
                fun from(viewGroup: ViewGroup): OptionViewHolder {
                    val inflater = LayoutInflater.from(viewGroup.context)
                    val binding =
                        ViewBottomSheetChooserItemBinding.inflate(inflater, viewGroup, false)
                    return OptionViewHolder(binding)
                }
            }
        }
    }

    class FormattableStringDiffUtilCallback : DiffUtil.ItemCallback<FormattableString>() {
        override fun areItemsTheSame(
            oldItem: FormattableString,
            newItem: FormattableString
        ): Boolean {
            return oldItem === newItem
        }

        override fun areContentsTheSame(
            oldItem: FormattableString,
            newItem: FormattableString
        ): Boolean {
            return oldItem == newItem
        }
    }

    data class BottomChooserState(
        val title: FormattableString,
        val options: List<FormattableString>,
        val listener: BottomChooserListener,
        val shouldShow: Boolean
    ) {
        companion object {
            val EMPTY_BOTTOM_CHOOSER = BottomChooserState(
                title = FormattableString.EMPTY_STRING,
                options = emptyList(),
                listener = BottomChooserListener.emptyListener,
                shouldShow = false
            )
        }
    }

    sealed class FormattableString {
        data class LiteralString(val string: String) : FormattableString() {
            override fun format(resources: Resources): String {
                if (this == EMPTY_STRING) {
                    return ""
                }
                return string
            }
        }

        data class ResourceString(
            @StringRes val stringRes: Int,
            val placeHolderStrings: List<String> = emptyList()
        ) : FormattableString() {
            override fun format(resources: Resources): String {
                return resources.getString(this.stringRes, *this.placeHolderStrings.toTypedArray())
            }
        }

        abstract fun format(resources: Resources): String

        companion object {
            fun from(@StringRes stringRes: Int): FormattableString {
                return ResourceString(stringRes)
            }

            fun from(string: String): FormattableString {
                return LiteralString(string)
            }

            val yes = from(android.R.string.yes)
            val no = from(android.R.string.no)

            val EMPTY_STRING = from("")
        }
    }
}

fun Resources.getString(fs: BottomSheetChooser.FormattableString?): String {
    return fs?.format(this) ?: ""
}
