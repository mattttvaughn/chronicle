package io.github.mattpvaughn.chronicle.features.settings

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.CompoundButton
import android.widget.FrameLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.databinding.PreferenceItemClickableBinding
import io.github.mattpvaughn.chronicle.databinding.PreferenceItemSwitchBinding
import io.github.mattpvaughn.chronicle.databinding.PreferenceItemTitleBinding

/**
 * A view which shows
 */
class SettingsList(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {

    private val prefsRepo = Injector.get().prefsRepo()
    private val prefAdapter = PreferencesListAdapter(prefsRepo)

    private var list: RecyclerView = RecyclerView(context, attrs).apply {
        adapter = prefAdapter
        layoutManager = LinearLayoutManager(context)
        layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
    }

    fun setPreferences(prefs: List<PreferenceModel>) {
        prefAdapter.submitList(prefs)
    }

    init {
        addView(list)
    }

    class PreferencesListAdapter(private val prefsRepo: PrefsRepo) : ListAdapter<PreferenceModel, RecyclerView.ViewHolder>(PreferenceItemDiffCallback()) {
        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): RecyclerView.ViewHolder {
            return when (prefIntMap.filterValues { it == viewType }.keys.map { it }[0]) {
                PreferenceType.CLICKABLE -> ClickablePreferenceViewHolder.from(parent)
                PreferenceType.BOOLEAN -> SwitchPreferenceViewHolder.from(parent, prefsRepo)
                PreferenceType.INTEGER -> ClickablePreferenceViewHolder.from(parent)
                PreferenceType.FLOAT -> ClickablePreferenceViewHolder.from(parent)
                PreferenceType.TITLE -> TitlePreferenceViewHolder.from(parent)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is ClickablePreferenceViewHolder) {
                holder.bind(getItem(position))
            }
            if (holder is SwitchPreferenceViewHolder) {
                holder.bind(getItem(position))
            }
            if (holder is TitlePreferenceViewHolder) {
                holder.bind(getItem(position))
            }
        }

        override fun getItemViewType(position: Int): Int {
            return prefIntMap.getValue(getItem(position).type)
        }

        class ClickablePreferenceViewHolder(val binding: PreferenceItemClickableBinding) :
            RecyclerView.ViewHolder(binding.root) {
            fun bind(preferenceModel: PreferenceModel) {
                binding.model = preferenceModel
                binding.executePendingBindings()
            }

            companion object {
                fun from(viewGroup: ViewGroup): ClickablePreferenceViewHolder {
                    val inflater = LayoutInflater.from(viewGroup.context)
                    val binding = PreferenceItemClickableBinding.inflate(inflater, viewGroup, false)
                    return ClickablePreferenceViewHolder(binding)
                }
            }
        }

        class SwitchPreferenceViewHolder(
            val binding: PreferenceItemSwitchBinding,
            private val prefsRepo: PrefsRepo
        ) : RecyclerView.ViewHolder(binding.root) {
            fun bind(preferenceModel: PreferenceModel) {
                binding.model = preferenceModel
                binding.preferenceSwitch.isChecked = prefsRepo.getBoolean(preferenceModel.key)
                binding.preferenceSwitch.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
                    prefsRepo.setBoolean(preferenceModel.key, isChecked)
                }
                binding.executePendingBindings()
            }

            companion object {
                fun from(
                    viewGroup: ViewGroup,
                    prefsRepo: PrefsRepo
                ): SwitchPreferenceViewHolder {
                    val inflater = LayoutInflater.from(viewGroup.context)
                    val binding = PreferenceItemSwitchBinding.inflate(inflater, viewGroup, false)
                    return SwitchPreferenceViewHolder(binding, prefsRepo)
                }
            }
        }

        class TitlePreferenceViewHolder(val binding: PreferenceItemTitleBinding) :
            RecyclerView.ViewHolder(binding.root) {
            fun bind(preferenceModel: PreferenceModel) {
                binding.model = preferenceModel
                binding.executePendingBindings()
            }

            companion object {
                fun from(viewGroup: ViewGroup): TitlePreferenceViewHolder {
                    val inflater = LayoutInflater.from(viewGroup.context)
                    val binding = PreferenceItemTitleBinding.inflate(inflater, viewGroup, false)
                    return TitlePreferenceViewHolder(binding)
                }
            }
        }


    }

    class PreferenceItemDiffCallback : DiffUtil.ItemCallback<PreferenceModel>() {
        override fun areItemsTheSame(oldItem: PreferenceModel, newItem: PreferenceModel): Boolean {
            return oldItem.title == newItem.title
        }

        override fun areContentsTheSame(oldItem: PreferenceModel, newItem: PreferenceModel): Boolean {
            return oldItem == newItem
        }
    }
}
