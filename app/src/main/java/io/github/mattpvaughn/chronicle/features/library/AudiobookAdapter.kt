package io.github.mattpvaughn.chronicle.features.library

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.databinding.GridItemAudiobookBinding

class AudiobookAdapter(
    private val isSquare: Boolean,
    private val isVertical: Boolean,
    private val audiobookClick: LibraryFragment.AudiobookClick
) : ListAdapter<Audiobook, AudiobookAdapter.ViewHolder>(AudiobookDiffCallback()) {

    private var serverConnected: Boolean = false

    override fun getItemId(position: Int): Long {
        return getItem(position).id.toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder.from(parent, isVertical, isSquare)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), audiobookClick, serverConnected)
    }

    fun setServerConnected(serverConnected: Boolean) {
        this.serverConnected = serverConnected
        notifyDataSetChanged()
    }

    class ViewHolder private constructor(
        val binding: GridItemAudiobookBinding,
        private val isVertical: Boolean,
        private val isSquare: Boolean
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            audiobook: Audiobook,
            audiobookClick: LibraryFragment.AudiobookClick,
            serverConnected: Boolean
        ) {
            binding.isSquare = isSquare
            binding.audiobook = audiobook
            binding.isVertical = isVertical
            binding.audiobookClick = audiobookClick
            binding.serverConnected = serverConnected
            binding.executePendingBindings()
        }

        companion object {
            fun from(viewGroup: ViewGroup, isVertical: Boolean, isSquare: Boolean): ViewHolder {
                val inflater = LayoutInflater.from(viewGroup.context)
                val binding = GridItemAudiobookBinding.inflate(inflater, viewGroup, false)
                return ViewHolder(binding, isVertical, isSquare)
            }
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