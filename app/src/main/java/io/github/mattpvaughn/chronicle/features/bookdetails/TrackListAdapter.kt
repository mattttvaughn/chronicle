package io.github.mattpvaughn.chronicle.features.bookdetails

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import io.github.mattpvaughn.chronicle.databinding.ListItemAudiobookTrackBinding

class TrackListAdapter(val clickListener: TrackClickListener) :
    ListAdapter<MediaItemTrack, TrackListAdapter.ViewHolder>(TrackDiffCallback()) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).id.toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder.from(parent)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), clickListener, activeTrackId)
    }

    private var activeTrackId = -1
    fun setActiveTrack(trackId: Int) {
        activeTrackId = trackId
        notifyDataSetChanged()
    }

    class ViewHolder private constructor(private val binding: ListItemAudiobookTrackBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(track: MediaItemTrack, clickListener: TrackClickListener, activeTrackId: Int) {
            binding.track = track
            binding.activeTrackId = activeTrackId
            binding.clickListener = clickListener
            binding.executePendingBindings()
        }

        companion object {
            fun from(parent: ViewGroup): ViewHolder {
                val layoutInflater = LayoutInflater.from(parent.context)
                val binding = ListItemAudiobookTrackBinding.inflate(layoutInflater, parent, false)
                return ViewHolder(binding)
            }
        }
    }
}

interface TrackClickListener {
    fun onClick(track: MediaItemTrack)
}

class TrackDiffCallback : DiffUtil.ItemCallback<MediaItemTrack>() {
    override fun areItemsTheSame(oldItem: MediaItemTrack, newItem: MediaItemTrack): Boolean {
        return oldItem.id == newItem.id
    }

    /**
     * For the purposes of [TrackListAdapter], the full content of the tracks should not be
     * compared, as certain fields like [MediaItemTrack.lastViewedAt] might differ but not require
     * a redraw of the view
     */
    override fun areContentsTheSame(oldItem: MediaItemTrack, newItem: MediaItemTrack): Boolean {
        return oldItem.index == newItem.index && oldItem.title == newItem.title
                && oldItem.media == newItem.media && oldItem.thumb == newItem.thumb
    }
}

