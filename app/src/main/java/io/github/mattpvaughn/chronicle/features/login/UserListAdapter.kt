package io.github.mattpvaughn.chronicle.features.login

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.data.sources.plex.model.PlexUser
import io.github.mattpvaughn.chronicle.databinding.ListItemUserBinding

class UserListAdapter(val clickListener: UserClickListener) :
    ListAdapter<PlexUser, UserListAdapter.UserViewHolder>(UserDiffCallback()) {

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(getItem(position), clickListener)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        return UserViewHolder.from(parent)
    }

    class UserViewHolder private constructor(val binding: ListItemUserBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(user: PlexUser, clickListener: UserClickListener) {
            binding.user = user
            Glide.with(binding.userThumb)
                .load(user.thumb)
                .placeholder(R.drawable.ic_person_white)
                .into(binding.userThumb)
            binding.clickListener = clickListener
            binding.executePendingBindings()
        }

        companion object {
            fun from(parent: ViewGroup): UserViewHolder {
                val layoutInflater = LayoutInflater.from(parent.context)
                val binding = ListItemUserBinding.inflate(layoutInflater, parent, false)
                return UserViewHolder(binding)
            }
        }
    }
}


class UserDiffCallback : DiffUtil.ItemCallback<PlexUser>() {
    override fun areItemsTheSame(oldItem: PlexUser, newItem: PlexUser): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: PlexUser, newItem: PlexUser): Boolean {
        return oldItem.title == newItem.title
    }
}

