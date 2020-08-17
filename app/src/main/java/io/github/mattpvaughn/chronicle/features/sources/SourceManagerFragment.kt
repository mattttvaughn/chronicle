package io.github.mattpvaughn.chronicle.features.sources

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import io.github.mattpvaughn.chronicle.application.MainActivity
import io.github.mattpvaughn.chronicle.data.sources.MediaSource
import io.github.mattpvaughn.chronicle.data.sources.SourceManager
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
        (requireActivity() as MainActivity).activityComponent.inject(this)
        super.onCreate(savedInstanceState)

        val binding = FragmentSourceManagerBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(
            viewModelStore,
            viewModelFactory
        ).get(SourceManagerViewModel::class.java)

        binding.viewModel = viewModel

        sourceListAdapter = SourceListAdapter(
            SourceClickListener { source ->
                Timber.i("Clicked source: $source")
            })

        binding.sourceList.adapter = sourceListAdapter

        viewModel.userMessage.observe(viewLifecycleOwner, Observer { message: Event<String> ->
            if (!message.hasBeenHandled) {
                Toast.makeText(context, message.getContentIfNotHandled(), Toast.LENGTH_SHORT).show()
            }
        })

        viewModel.sources.observe(viewLifecycleOwner, Observer { libraries ->
            libraries?.apply {
                sourceListAdapter.submitList(this)
            }
        })

        binding.addNewSource.setOnClickListener {
            viewModel.addSource()
        }

        return binding.root
    }

}

class SourceClickListener(val clickListener: (MediaSource) -> Unit) {
    fun onClick(mediaSource: MediaSource) = clickListener(mediaSource)
}
