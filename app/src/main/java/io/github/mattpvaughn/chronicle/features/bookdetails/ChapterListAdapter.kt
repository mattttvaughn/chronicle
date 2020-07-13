package io.github.mattpvaughn.chronicle.features.bookdetails

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.data.model.Chapter
import io.github.mattpvaughn.chronicle.databinding.ListItemAudiobookTrackBinding
import io.github.mattpvaughn.chronicle.databinding.ListItemDiscNumberSectionHeadingBinding
import io.github.mattpvaughn.chronicle.features.bookdetails.ChapterListAdapter.ChapterListModel.ChapterItemModel
import io.github.mattpvaughn.chronicle.features.bookdetails.ChapterListAdapter.ChapterListModel.SectionHeaderWrapper
import io.github.mattpvaughn.chronicle.views.BottomSheetChooser
import timber.log.Timber

class ChapterListAdapter(val clickListener: TrackClickListener) :
    ListAdapter<ChapterListAdapter.ChapterListModel, RecyclerView.ViewHolder>(
        ChapterItemDiffCallback()
    ) {

    /** Wrapper around [Chapter] and a section header */
    sealed class ChapterListModel {
        companion object {
            const val CHAPTER_TYPE = 1
            const val SECTION_HEADER_TYPE = 2
        }

        internal class ChapterItemModel(val chapter: Chapter) : ChapterListModel()
        internal class SectionHeaderWrapper(val section: SectionHeaderModel) : ChapterListModel()
    }

    class SectionHeaderModel(val text: BottomSheetChooser.FormattableString)

    fun submitChapters(list: List<Chapter>?) {
        Timber.i("Submitted chapters: $list")
        // Add disc headers only if necessary. We use disc numbers if the final track is owned by
        // a disc other than 1 (discNumber defaults to 1)
        if (!list.isNullOrEmpty() && list.last().discNumber > 1) {
            // iterate through chapters, insert section headers as indicated by [Chapter.discNumber]
            val listWithSections = mutableListOf<ChapterListModel>()
            listWithSections.add(
                SectionHeaderWrapper(
                    SectionHeaderModel(
                        BottomSheetChooser.FormattableString.ResourceString(
                            R.string.disc_number, listOf("1")
                        )
                    )
                )
            )
            listWithSections.add(ChapterItemModel(list.first()))
            list.fold(list.first()) { prev, curr ->
                // avoid edge cases at start/end, id is guaranteed to be different for unique
                // chapters/tracks by Plex
                if (curr.id == prev.id) {
                    return@fold curr
                }
                if (curr.discNumber > prev.discNumber) {
                    listWithSections.add(
                        SectionHeaderWrapper(
                            SectionHeaderModel(
                                BottomSheetChooser.FormattableString.ResourceString(
                                    R.string.disc_number,
                                    listOf(curr.discNumber.toString())
                                )
                            )
                        )
                    )
                }
                listWithSections.add(ChapterItemModel(curr))

                curr
            }

            super.submitList(listWithSections)
        } else {
            if (list.isNullOrEmpty()) {
                super.submitList(mutableListOf<ChapterListModel>())
            } else {
                super.submitList(list.map { ChapterItemModel(it) })
            }
        }
    }

    override fun submitList(list: MutableList<ChapterListModel>?) {
        throw IllegalAccessException("Clients must use ChapterListAdapter.submitChapters()")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            ChapterListModel.CHAPTER_TYPE -> ChapterViewHolder.from(parent, clickListener)
            ChapterListModel.SECTION_HEADER_TYPE -> SectionHeaderViewHolder.from(parent)
            else -> throw NoWhenBranchMatchedException()
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ChapterItemModel -> ChapterListModel.CHAPTER_TYPE
            is SectionHeaderWrapper -> ChapterListModel.SECTION_HEADER_TYPE
            else -> throw NoWhenBranchMatchedException()
        }
    }

    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (viewHolder) {
            is ChapterViewHolder -> viewHolder.bind((item as ChapterItemModel).chapter)
            is SectionHeaderViewHolder -> viewHolder.bind((item as SectionHeaderWrapper).section)
            else -> throw NoWhenBranchMatchedException()
        }
    }

    class ChapterViewHolder private constructor(
        private val binding: ListItemAudiobookTrackBinding,
        private val clickListener: TrackClickListener
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(chapter: Chapter) {
            binding.chapter = chapter
            binding.clickListener = clickListener
            binding.executePendingBindings()
        }

        companion object {
            fun from(parent: ViewGroup, clickListener: TrackClickListener): ChapterViewHolder {
                val layoutInflater = LayoutInflater.from(parent.context)
                val binding = ListItemAudiobookTrackBinding.inflate(layoutInflater, parent, false)
                return ChapterViewHolder(binding, clickListener)
            }
        }
    }


    class SectionHeaderViewHolder private constructor(
        private val binding: ListItemDiscNumberSectionHeadingBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(heading: SectionHeaderModel) {
            binding.sectionHeader = heading
            binding.executePendingBindings()
        }

        companion object {
            fun from(parent: ViewGroup): SectionHeaderViewHolder {
                val layoutInflater = LayoutInflater.from(parent.context)
                val binding =
                    ListItemDiscNumberSectionHeadingBinding.inflate(layoutInflater, parent, false)
                return SectionHeaderViewHolder(binding)
            }
        }
    }

    private class ChapterItemDiffCallback : DiffUtil.ItemCallback<ChapterListModel>() {
        override fun areItemsTheSame(
            oldItem: ChapterListModel,
            newItem: ChapterListModel
        ): Boolean {
            return when {
                oldItem is ChapterItemModel && newItem is ChapterItemModel -> {
                    oldItem.chapter.id == newItem.chapter.id
                }
                oldItem is SectionHeaderWrapper && newItem is SectionHeaderWrapper -> {
                    oldItem.section.text == newItem.section.text
                }
                else -> false
            }
        }

        /**
         * For the purposes of [ChapterListAdapter], the full content of the tracks should not be
         * compared, as certain fields like might differ but not require a redraw of the view
         */
        override fun areContentsTheSame(
            oldItem: ChapterListModel,
            newItem: ChapterListModel
        ): Boolean {
            return when {
                oldItem is ChapterItemModel && newItem is ChapterItemModel -> {
                    oldItem.chapter.index == newItem.chapter.index && oldItem.chapter.title == newItem.chapter.title
                }
                oldItem is SectionHeaderWrapper && newItem is SectionHeaderWrapper -> {
                    oldItem.section.text == newItem.section.text
                }
                else -> false
            }
        }
    }
}

interface TrackClickListener {
    fun onClick(chapter: Chapter)
}

