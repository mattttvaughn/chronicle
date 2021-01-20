package io.github.mattpvaughn.chronicle.features.sources

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetBehavior
import io.github.mattpvaughn.chronicle.application.MainActivity
import io.github.mattpvaughn.chronicle.data.sources.MediaSource
import io.github.mattpvaughn.chronicle.data.sources.SourceManager
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexLoginRepo.Companion.ARG_ERROR_MESSAGE_NO_PLEX_SOURCE_FOUND
import io.github.mattpvaughn.chronicle.databinding.FragmentSourceManagerBinding
import io.github.mattpvaughn.chronicle.util.Event
import timber.log.Timber
import javax.inject.Inject


class SourceManagerFragment : Fragment() {

    companion object {
        @JvmStatic
        fun newInstance() = SourceManagerFragment()

        const val TAG = "SourceManagerFragment"
    }

    @Inject
    lateinit var viewModelFactory: SourceManagerViewModel.Factory

    private lateinit var viewModel: SourceManagerViewModel

    private lateinit var sourceListAdapter: SourceListAdapter

    @Inject
    lateinit var sourceManager: SourceManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        (requireActivity() as MainActivity).activityComponent!!.inject(this)
        super.onCreate(savedInstanceState)

        val binding = FragmentSourceManagerBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner

        val errorMessage: String? = arguments?.getString(ARG_ERROR_MESSAGE_NO_PLEX_SOURCE_FOUND)

        viewModel = ViewModelProvider(
            viewModelStore,
            viewModelFactory
        ).get(SourceManagerViewModel::class.java)

        binding.viewModel = viewModel

        val bottomSheetParams = binding.sourceEditor.layoutParams as CoordinatorLayout.LayoutParams
        val bottomSheetBehavior = bottomSheetParams.behavior as BottomSheetBehavior
        sourceListAdapter = SourceListAdapter(
            SourceClickListener { source ->
                Timber.i("Clicked source: $source")
                viewModel.showEditSource(source)
            })

        binding.sourceList.adapter = sourceListAdapter

        viewModel.userMessage.observe(viewLifecycleOwner) { message: Event<String> ->
            if (!message.hasBeenHandled) {
                Toast.makeText(context, message.getContentIfNotHandled(), Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.sources.observe(viewLifecycleOwner) { libraries ->
            Timber.i("Updated sources")
            sourceListAdapter.submitList(libraries ?: emptyList())
        }

        binding.addNewSource.setOnClickListener {
            viewModel.addSource()
        }

        errorMessage?.let {
            viewModel.showErrorMessage(it)
        }

        viewModel.source.observe(viewLifecycleOwner) { sourceWrapper ->
            Timber.i("Opening source: $sourceWrapper")
            bottomSheetBehavior.state = if (sourceWrapper.isEmpty) {
                BottomSheetBehavior.STATE_HIDDEN
            } else {
                BottomSheetBehavior.STATE_EXPANDED
            }
        }

        return binding.root
    }

}

class SourceClickListener(val clickListener: (MediaSource) -> Unit) {
    fun onClick(mediaSource: MediaSource) = clickListener(mediaSource)
}
