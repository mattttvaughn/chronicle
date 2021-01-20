package io.github.mattpvaughn.chronicle.features.library

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.VIEW_STYLE_COVER_GRID
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.VIEW_STYLE_DETAILS_LIST
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.VIEW_STYLE_TEXT_LIST
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.databinding.GridItemAudiobookBinding
import io.github.mattpvaughn.chronicle.databinding.ListItemAudiobookTextOnlyBinding
import io.github.mattpvaughn.chronicle.databinding.ListItemAudiobookWithDetailsBinding

class AudiobookAdapter(
    initialViewStyle: String,
    private val isVertical: Boolean,
    private val isSquare: Boolean,
    private val audiobookClick: LibraryFragment.AudiobookClick
) : ListAdapter<Audiobook, RecyclerView.ViewHolder>(AudiobookDiffCallback()) {

    private val COVER_GRID = 1
    private val TEXT_ONLY = 2
    private val DETAILS = 3
    var viewStyle: String = initialViewStyle
        set(value) {
            viewStyleInt = when (value) {
                VIEW_STYLE_COVER_GRID -> COVER_GRID
                VIEW_STYLE_TEXT_LIST -> TEXT_ONLY
                VIEW_STYLE_DETAILS_LIST -> DETAILS
                else -> throw IllegalStateException("Unknown view style")
            }
            notifyDataSetChanged()
            field = value
        }
    private var viewStyleInt: Int = when (initialViewStyle) {
        VIEW_STYLE_COVER_GRID -> COVER_GRID
        VIEW_STYLE_TEXT_LIST -> TEXT_ONLY
        VIEW_STYLE_DETAILS_LIST -> DETAILS
        else -> throw IllegalStateException("Unknown view style")
    }


    private var connectedServerIds: List<Long> = emptyList()

    override fun getItemId(position: Int): Long {
        return getItem(position).id.toLong()
    }

    override fun getItemViewType(position: Int): Int {
        return viewStyleInt
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            COVER_GRID -> ViewHolder.from(parent, isVertical, isSquare)
            TEXT_ONLY -> TextOnlyViewHolder.from(parent)
            DETAILS -> DetailsStyleViewHolder.from(parent)
            else -> throw IllegalStateException("Unknown view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ViewHolder -> {
                holder.bind(getItem(position), audiobookClick, connectedServerIds)
            }
            is TextOnlyViewHolder -> {
                holder.bind(getItem(position), audiobookClick, connectedServerIds)
            }
            is DetailsStyleViewHolder -> {
                holder.bind(getItem(position), audiobookClick, connectedServerIds, isSquare)
            }
            else -> throw IllegalStateException("Unknown view type")
        }
    }

    fun setActiveConnections(connections: List<Long>) {
        this.connectedServerIds = connections
        notifyDataSetChanged()
    }

    class ViewHolder(
        val binding: GridItemAudiobookBinding,
        private val isVertical: Boolean,
        private val isSquare: Boolean
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            audiobook: Audiobook,
            audiobookClick: LibraryFragment.AudiobookClick,
            connectedServerIds: List<Long>
        ) {
            val source = Injector.get().sourceManager()
            binding.source = source.getSourceById(audiobook.source)
            binding.isSquare = isSquare
            binding.audiobook = audiobook
            binding.isVertical = isVertical
            binding.audiobookClick = audiobookClick
            binding.serverConnected = connectedServerIds.contains(audiobook.source)
            binding.executePendingBindings()
        }

        companion object {
            fun from(
                viewGroup: ViewGroup,
                isVertical: Boolean,
                isSquare: Boolean
            ): ViewHolder {
                val inflater = LayoutInflater.from(viewGroup.context)
                val binding = GridItemAudiobookBinding.inflate(inflater, viewGroup, false)
                return ViewHolder(binding, isVertical, isSquare)
            }
        }
    }

    class TextOnlyViewHolder(val binding: ListItemAudiobookTextOnlyBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(
            audiobook: Audiobook,
            audiobookClick: LibraryFragment.AudiobookClick,
            connectedServerIds: List<Long>
        ) {
            val sourceManager = Injector.get().sourceManager()
            binding.source = sourceManager.getSourceById(audiobook.source)
            binding.serverConnected = connectedServerIds.contains(audiobook.source)
            binding.audiobook = audiobook
            binding.audiobookClick = audiobookClick
            binding.executePendingBindings()
        }

        companion object {
            fun from(viewGroup: ViewGroup): TextOnlyViewHolder {
                val inflater = LayoutInflater.from(viewGroup.context)
                val binding =
                    ListItemAudiobookTextOnlyBinding.inflate(inflater, viewGroup, false)
                return TextOnlyViewHolder(binding)
            }
        }
    }
}

class DetailsStyleViewHolder(
    val binding: ListItemAudiobookWithDetailsBinding
) : RecyclerView.ViewHolder(binding.root) {
    fun bind(
        audiobook: Audiobook,
        audiobookClick: LibraryFragment.AudiobookClick,
        connectedServerIds: List<Long>,
        isSquare: Boolean
    ) {
        binding.isSquare = isSquare
        binding.audiobook = audiobook
        binding.audiobookClick = audiobookClick
        binding.serverConnected = connectedServerIds.contains(audiobook.source)
        binding.executePendingBindings()
    }

    companion object {
        fun from(viewGroup: ViewGroup): DetailsStyleViewHolder {
            val inflater = LayoutInflater.from(viewGroup.context)
            val binding =
                ListItemAudiobookWithDetailsBinding.inflate(inflater, viewGroup, false)
            return DetailsStyleViewHolder(binding)
        }
    }
}

class AudiobookDiffCallback : DiffUtil.ItemCallback<Audiobook>() {
    override fun areItemsTheSame(oldItem: Audiobook, newItem: Audiobook): Boolean {
        return oldItem.id == newItem.id
    }

    /** Changes which require a redraw of the view */
    override fun areContentsTheSame(oldItem: Audiobook, newItem: Audiobook): Boolean {
        return oldItem.thumb == newItem.thumb && oldItem.title == newItem.title
                && oldItem.author == newItem.author && oldItem.isCached == newItem.isCached
                && oldItem.progress == newItem.progress
    }
}
