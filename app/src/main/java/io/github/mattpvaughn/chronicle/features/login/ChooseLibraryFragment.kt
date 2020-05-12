package io.github.mattpvaughn.chronicle.features.login

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import io.github.mattpvaughn.chronicle.application.ChronicleApplication
import io.github.mattpvaughn.chronicle.data.model.Library
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME
import io.github.mattpvaughn.chronicle.data.plex.IPlexLoginRepo
import io.github.mattpvaughn.chronicle.data.plex.PlexConfig
import io.github.mattpvaughn.chronicle.data.plex.PlexPrefsRepo
import io.github.mattpvaughn.chronicle.databinding.OnboardingPlexChooseLibraryBinding
import javax.inject.Inject


class ChooseLibraryFragment : Fragment() {

    companion object {
        @JvmStatic
        fun newInstance() = ChooseLibraryFragment()
    }

    @Inject
    lateinit var viewModelFactory: ChooseLibraryViewModel.Factory

    private lateinit var viewModel: ChooseLibraryViewModel

    private lateinit var libraryAdapter: LibraryListAdapter

    @Inject
    lateinit var plexConfig: PlexConfig

    @Inject
    lateinit var plexPrefs: PlexPrefsRepo

    @Inject
    lateinit var plexLoginRepo: IPlexLoginRepo

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        ((activity as Activity).application as ChronicleApplication).appComponent.inject(this)
        super.onCreate(savedInstanceState)

        val binding = OnboardingPlexChooseLibraryBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(
            viewModelStore,
            viewModelFactory
        ).get(ChooseLibraryViewModel::class.java)

        binding.chooseLibraryViewModel = viewModel


        libraryAdapter =
            LibraryListAdapter(
                LibraryClickListener { library ->
                    Log.i(APP_NAME, "Library name: $library")
                    plexLoginRepo.chooseLibrary(library)
                })
        binding.libraryList.adapter = libraryAdapter

        viewModel.userMessage.observe(viewLifecycleOwner, Observer { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        })

        viewModel.libraries.observe(viewLifecycleOwner, Observer { libraries ->
            libraries?.apply {
                libraryAdapter.submitList(this)
            }
        })

        return binding.root
    }

}

class LibraryClickListener(val clickListener: (library: Library) -> Unit) {
    fun onClick(library: Library) = clickListener(library)
}
