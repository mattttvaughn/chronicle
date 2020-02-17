package io.github.mattpvaughn.chronicle.views

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Context.LAYOUT_INFLATER_SERVICE
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
class BottomSheetChooser(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {

    fun setOptions(options: List<String>) {
        val binding = findViewById<View>(R.id.bottom_sheet).tag as ViewBottomSheetChooserBinding
        binding.options = options
        binding.executePendingBindings()
    }

    fun setTitle(newTitle: String) {
        val binding = findViewById<View>(R.id.bottom_sheet).tag as ViewBottomSheetChooserBinding
        binding.title = newTitle
        binding.executePendingBindings()
    }

    fun setOptionsSelectedListener(listener: ItemSelectedListener) {
        val binding = findViewById<View>(R.id.bottom_sheet).tag as ViewBottomSheetChooserBinding
        binding.listener = listener
        binding.executePendingBindings()
    }

    interface ItemSelectedListener {
        fun onItemSelected(itemName: String)

        companion object {
            val emptyListener = object : ItemSelectedListener {
                override fun onItemSelected(itemName: String) {}
            }
        }
    }

    private val optionsAdapter = OptionsListAdapter(ItemSelectedListener.emptyListener)

    fun hide() {
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
                postDelayed({ binding.tapToClose.visibility = View.GONE }, animDuration)

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
        binding.title = "Title"
        binding.listener = ItemSelectedListener.emptyListener
        binding.tapToClose.setOnClickListener { hide() }
        binding.bottomSheetOptions.adapter = optionsAdapter
        binding.executePendingBindings()
    }

    class OptionsListAdapter(private var listener: ItemSelectedListener) :
        ListAdapter<String, OptionsListAdapter.OptionViewHolder>(StringDiffUtilCallback()) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OptionViewHolder {
            return OptionViewHolder.from(parent, listener)
        }

        fun setListener(_listener: ItemSelectedListener) {
            listener = _listener
        }

        override fun onBindViewHolder(holder: OptionViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        class OptionViewHolder(
            val binding: ViewBottomSheetChooserItemBinding,
            private val listener: ItemSelectedListener
        ) :
            RecyclerView.ViewHolder(binding.root) {
            fun bind(option: String) {
                binding.option = option
                binding.listener = listener
                binding.executePendingBindings()
            }

            companion object {
                fun from(viewGroup: ViewGroup, listener: ItemSelectedListener): OptionViewHolder {
                    val inflater = LayoutInflater.from(viewGroup.context)
                    val binding =
                        ViewBottomSheetChooserItemBinding.inflate(inflater, viewGroup, false)
                    return OptionViewHolder(binding, listener)
                }
            }
        }


    }

    class StringDiffUtilCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem === newItem
        }

        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }
    }
}
