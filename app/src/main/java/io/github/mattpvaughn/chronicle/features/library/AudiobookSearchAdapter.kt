package io.github.mattpvaughn.chronicle.features.library

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.databinding.ListItemSearchResultAudiobookBinding
import io.github.mattpvaughn.chronicle.features.library.LibraryFragment.AudiobookClick

class AudiobookSearchAdapter(private val audiobookClick: AudiobookClick) : ListAdapter<Audiobook, AudiobookSearchAdapter.ViewHolder>(AudiobookDiffCallback()) {

    private var activeSourceIds: List<Long> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder.from(parent)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), audiobookClick, activeSourceIds)
    }

    fun setActiveConnections(connectedSourceIds: List<Long>) {
        activeSourceIds = connectedSourceIds
        notifyDataSetChanged()
    }

    class ViewHolder private constructor(val binding: ListItemSearchResultAudiobookBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(
            audiobook: Audiobook,
            searchResultClick: AudiobookClick,
            connectedSourceIds: List<Long>
        ) {
            val source = Injector.get().sourceManager().getSourceById(audiobook.source)
            binding.source = source
            binding.audiobook = audiobook
            binding.searchResultClick = searchResultClick
            binding.serverConnected = connectedSourceIds.contains(audiobook.source)
            binding.executePendingBindings()
        }

        companion object {
            fun from(viewGroup: ViewGroup): ViewHolder {
                val inflater = LayoutInflater.from(viewGroup.context)
                val binding =
                    ListItemSearchResultAudiobookBinding.inflate(inflater, viewGroup, false)
                return ViewHolder(binding)
            }
        }
    }
}

