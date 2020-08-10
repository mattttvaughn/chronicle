package io.github.mattpvaughn.chronicle.features.login

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import io.github.mattpvaughn.chronicle.application.MainActivity
import io.github.mattpvaughn.chronicle.data.model.ServerModel
import io.github.mattpvaughn.chronicle.data.sources.SourceManager
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexLibrarySource
import io.github.mattpvaughn.chronicle.databinding.OnboardingPlexChooseServerBinding
import javax.inject.Inject


class ChooseServerFragment : Fragment() {

    companion object {
        @JvmStatic
        fun newInstance() = ChooseServerFragment()

        const val TAG = "Choose server fragment"
    }

    @Inject
    lateinit var viewModelFactory: ChooseServerViewModel.Factory
    private lateinit var viewModel: ChooseServerViewModel

    @Inject
    lateinit var sourceManager: SourceManager

    private lateinit var serverAdapter: ServerListAdapter

    override fun onAttach(context: Context) {
        (requireActivity() as MainActivity).activityComponent.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreate(savedInstanceState)

        val binding = OnboardingPlexChooseServerBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner

        val source = sourceManager.getSourcesOfType<PlexLibrarySource>().firstOrNull {
            !it.isAuthorized()
        }

        checkNotNull(source) { "No source found! Crashing is probably the wrong behavior but..." }

        viewModelFactory.plexLibrarySource = source
        viewModel = ViewModelProvider(
            viewModelStore,
            viewModelFactory
        ).get(ChooseServerViewModel::class.java)

        serverAdapter = ServerListAdapter(ServerClickListener { serverModel ->
            viewModel.chooseServer(serverModel)
        })

        binding.serverList.adapter = serverAdapter

        viewModel.servers.observe(viewLifecycleOwner, Observer { servers ->
            servers?.let {
                serverAdapter.submitList(it)
            }
        })

        viewModel.userMessage.observe(viewLifecycleOwner, Observer {
            if (it.hasBeenHandled) {
                return@Observer
            }
            Toast.makeText(requireContext(), it.getContentIfNotHandled(), LENGTH_SHORT).show()
        })

        binding.chooseServerViewModel = viewModel
        return binding.root
    }
}

class ServerClickListener(val clickListener: (serverModel: ServerModel) -> Unit) {
    fun onClick(server: ServerModel) = clickListener(server)
}
