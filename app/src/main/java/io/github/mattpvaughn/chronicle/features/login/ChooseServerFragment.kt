package io.github.mattpvaughn.chronicle.features.login

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import io.github.mattpvaughn.chronicle.application.ChronicleApplication
import io.github.mattpvaughn.chronicle.data.model.ServerModel
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME
import io.github.mattpvaughn.chronicle.data.plex.IPlexLoginRepo
import io.github.mattpvaughn.chronicle.data.plex.PlexConnectionChooser
import io.github.mattpvaughn.chronicle.databinding.OnboardingPlexChooseServerBinding
import javax.inject.Inject


class ChooseServerFragment : Fragment() {

    companion object {
        @JvmStatic
        fun newInstance() = ChooseServerFragment()
    }

    @Inject
    lateinit var viewModelFactory: ChooseServerViewModel.Factory
    private lateinit var viewModel: ChooseServerViewModel

    @Inject
    lateinit var connectionChooser: PlexConnectionChooser

    @Inject
    lateinit var plexLoginRepo: IPlexLoginRepo

    private lateinit var serverAdapter: ServerListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        ((activity as Activity).application as ChronicleApplication)
            .appComponent
            .inject(this)
        super.onCreate(savedInstanceState)

        val binding = OnboardingPlexChooseServerBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(
            viewModelStore,
            viewModelFactory
        ).get(ChooseServerViewModel::class.java)

        serverAdapter =
            ServerListAdapter(
                ServerClickListener { serverModel ->
                    Log.i(APP_NAME, "Chose server: $serverModel")
                    connectionChooser.addPotentialConnections(serverModel.connections)
                    plexLoginRepo.chooseServer(serverModel)
                })

        binding.serverList.adapter = serverAdapter

        viewModel.servers.observe(viewLifecycleOwner, Observer { servers ->
            servers?.let {
                serverAdapter.submitList(it)
            }
        })
        binding.chooseServerViewModel = viewModel
        return binding.root
    }
}

class ServerClickListener(val clickListener: (serverModel: ServerModel) -> Unit) {
    fun onClick(server: ServerModel) = clickListener(server)
}
