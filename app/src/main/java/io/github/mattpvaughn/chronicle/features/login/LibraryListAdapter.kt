package io.github.mattpvaughn.chronicle.features.login

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.mattpvaughn.chronicle.data.model.Library
import io.github.mattpvaughn.chronicle.databinding.ListItemLibraryBinding

class LibraryListAdapter(val clickListener: LibraryClickListener) :
    ListAdapter<Library, LibraryListAdapter.LibraryViewHolder>(
        LibraryDiffCallback()
    ) {

    override fun onBindViewHolder(holder: LibraryViewHolder, position: Int) {
        holder.bind(getItem(position), clickListener)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LibraryViewHolder {
        return LibraryViewHolder.from(
            parent
        )
    }

    class LibraryViewHolder private constructor(val binding: ListItemLibraryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(library: Library, clickListener: LibraryClickListener) {
            binding.library = library
            binding.clickListener = clickListener
            binding.executePendingBindings()
        }

        companion object {
            fun from(parent: ViewGroup): LibraryViewHolder {
                val layoutInflater = LayoutInflater.from(parent.context)
                val binding = ListItemLibraryBinding.inflate(layoutInflater, parent, false)
                return LibraryViewHolder(
                    binding
                )
            }
        }
    }
}


class LibraryDiffCallback : DiffUtil.ItemCallback<Library>() {
    override fun areItemsTheSame(oldItem: Library, newItem: Library): Boolean {
        return oldItem.name == newItem.name
    }

    override fun areContentsTheSame(oldItem: Library, newItem: Library): Boolean {
        return oldItem == newItem
    }
}

