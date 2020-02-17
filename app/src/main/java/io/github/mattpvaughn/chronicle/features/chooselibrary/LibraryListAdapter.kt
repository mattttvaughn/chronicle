package io.github.mattpvaughn.chronicle.features.chooselibrary

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.mattpvaughn.chronicle.databinding.ListItemLibraryBinding

class LibraryListAdapter(val clickListener: LibraryClickListener) :
    ListAdapter<LibraryModel, LibraryListAdapter.LibraryViewHolder>(LibraryDiffCallback()) {

    override fun onBindViewHolder(holder: LibraryViewHolder, position: Int) {
        holder.bind(getItem(position), clickListener)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LibraryViewHolder {
        return LibraryViewHolder.from(parent)
    }

    class LibraryViewHolder private constructor(val binding: ListItemLibraryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(library: LibraryModel, clickListener: LibraryClickListener) {
            binding.library = library
            binding.clickListener = clickListener
            binding.executePendingBindings()
        }

        companion object {
            fun from(parent: ViewGroup): LibraryViewHolder {
                val layoutInflater = LayoutInflater.from(parent.context)
                val binding = ListItemLibraryBinding.inflate(layoutInflater, parent, false)
                return LibraryViewHolder(binding)
            }
        }
    }
}


class LibraryDiffCallback : DiffUtil.ItemCallback<LibraryModel>() {
    override fun areItemsTheSame(oldItem: LibraryModel, newItem: LibraryModel): Boolean {
        return oldItem.name == newItem.name
    }

    override fun areContentsTheSame(oldItem: LibraryModel, newItem: LibraryModel): Boolean {
        return oldItem == newItem
    }
}

