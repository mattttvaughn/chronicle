package io.github.mattpvaughn.chronicle.features.chooseserver

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.data.plex.PlexPrefsRepo
import io.github.mattpvaughn.chronicle.data.plex.PlexRequestSingleton
import io.github.mattpvaughn.chronicle.databinding.ActivityChooseServerBinding
import io.github.mattpvaughn.chronicle.features.chooselibrary.ChooseLibraryActivity
import kotlinx.coroutines.InternalCoroutinesApi


class ChooseServerActivity : AppCompatActivity() {

    private lateinit var viewModel: ChooseServerViewModel
    private lateinit var serverAdapter: ServerListAdapter

    @UseExperimental(InternalCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs: PlexPrefsRepo = Injector.get().plexPrefs()
        viewModel = ChooseServerViewModel(prefs)

        val binding = DataBindingUtil.setContentView<ActivityChooseServerBinding>(
            this,
            R.layout.activity_choose_server
        )
        binding.lifecycleOwner = this

        serverAdapter = ServerListAdapter(ServerClickListener { serverModel ->
            prefs.putServer(serverModel)
            PlexRequestSingleton.connectionSet.addAll(serverModel.connections)
            chooseLibrary()
        })

        binding.serverList.adapter = serverAdapter

        viewModel.chooseLibrary.observe(this, Observer {
            if (it) {
                chooseLibrary()
            }
        })
        viewModel.servers.observe(this, Observer<List<ServerModel>> { servers ->
            servers?.apply {
                serverAdapter.submitList(servers)
            }
        })
        binding.chooseServerViewModel = viewModel
    }

    private fun chooseLibrary() {
        startActivity(Intent(this, ChooseLibraryActivity::class.java))
    }

    override fun onBackPressed() {
        Injector.get().plexPrefs().removeServer()
        super.onBackPressed()
    }
}

class ServerClickListener(val clickListener: (serverModel: ServerModel) -> Unit) {
    fun onClick(server: ServerModel) = clickListener(server)
}
