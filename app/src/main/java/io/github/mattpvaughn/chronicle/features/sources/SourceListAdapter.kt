package io.github.mattpvaughn.chronicle.features.sources

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.mattpvaughn.chronicle.data.sources.MediaSource
import io.github.mattpvaughn.chronicle.databinding.ListItemSourceBinding

class SourceListAdapter(val clickListener: SourceClickListener) :
    ListAdapter<MediaSource, SourceListAdapter.SourceViewHolder>(
        ServerDiffCallback()
    ) {

    override fun onBindViewHolder(holder: SourceViewHolder, position: Int) {
        holder.bind(getItem(position), clickListener)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SourceViewHolder {
        return SourceViewHolder.from(parent)
    }

    class SourceViewHolder private constructor(val binding: ListItemSourceBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(source: MediaSource, clickListener: SourceClickListener) {
            binding.source = source
            binding.clickListener = clickListener
            binding.executePendingBindings()
        }

        companion object {
            fun from(parent: ViewGroup): SourceViewHolder {
                val layoutInflater = LayoutInflater.from(parent.context)
                val binding = ListItemSourceBinding.inflate(layoutInflater, parent, false)
                return SourceViewHolder(binding)
            }
        }
    }
}


class ServerDiffCallback : DiffUtil.ItemCallback<MediaSource>() {
    override fun areItemsTheSame(oldItem: MediaSource, newItem: MediaSource): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: MediaSource, newItem: MediaSource): Boolean {
        return oldItem.id == newItem.id
    }
}

