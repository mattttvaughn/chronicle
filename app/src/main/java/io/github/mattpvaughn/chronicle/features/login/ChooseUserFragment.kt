package io.github.mattpvaughn.chronicle.features.login

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import io.github.mattpvaughn.chronicle.application.MainActivity
import io.github.mattpvaughn.chronicle.data.sources.SourceManager
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexLibrarySource
import io.github.mattpvaughn.chronicle.data.sources.plex.model.PlexUser
import io.github.mattpvaughn.chronicle.databinding.OnboardingPlexChooseUserBinding
import javax.inject.Inject

/** Handles the picking of user profiles. */
class ChooseUserFragment : Fragment() {

    companion object {
        @JvmStatic
        fun newInstance() = ChooseUserFragment()

        const val TAG = "Choose user fragment"
    }

    @Inject
    lateinit var viewModelFactory: ChooseUserViewModel.Factory
    private lateinit var viewModel: ChooseUserViewModel

    @Inject
    lateinit var sourceManager: SourceManager

    private lateinit var userListAdapter: UserListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        (requireActivity() as MainActivity).activityComponent.inject(this)
        super.onCreate(savedInstanceState)

        val binding = OnboardingPlexChooseUserBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner

        viewModelFactory.plexLibrarySource =
            sourceManager.getSourcesOfType<PlexLibrarySource>().firstOrNull {
                !it.isAuthorized()
            }!!

        viewModel = ViewModelProvider(
            viewModelStore,
            viewModelFactory
        ).get(ChooseUserViewModel::class.java)

        userListAdapter = UserListAdapter(UserClickListener { user ->
            viewModel.pickUser(user)
        })
        binding.userList.adapter = userListAdapter

        binding.pinEdittext.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s != null) {
                    viewModel.setPinData(s)
                }
            }
        })

        viewModel.userMessage.observe(viewLifecycleOwner, Observer {
            if (it.hasBeenHandled) {
                return@Observer
            }
            Toast.makeText(requireContext(), it.getContentIfNotHandled(), LENGTH_SHORT).show()
        })

        binding.pinToolbar.setNavigationOnClickListener {
            hidePinEntryScreen()
        }

        binding.pinEdittext.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                viewModel.submitPin()
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }

        viewModel.pinErrorMessage.observe(viewLifecycleOwner, Observer {
            if (!it.isNullOrEmpty()) {
                binding.pinEdittext.error = it
            } else {
                binding.pinEdittext.error = null
            }
        })

        binding.viewModel = viewModel
        return binding.root
    }

    fun isPinEntryScreenVisible(): Boolean {
        return viewModel.showPin.value == true
    }

    fun hidePinEntryScreen() {
        viewModel.hidePinScreen()
    }
}

class UserClickListener(val clickListener: (user: PlexUser) -> Unit) {
    fun onClick(user: PlexUser) = clickListener(user)
}
