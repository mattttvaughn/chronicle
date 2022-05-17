package io.github.mattpvaughn.chronicle.features.login

import android.app.Activity
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
import io.github.mattpvaughn.chronicle.application.ChronicleApplication
import io.github.mattpvaughn.chronicle.data.sources.plex.IPlexLoginRepo
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexConfig
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
    lateinit var plexLoginRepo: IPlexLoginRepo

    @Inject
    lateinit var plexConfig: PlexConfig

    private lateinit var userListAdapter: UserListAdapter

    private var binding: OnboardingPlexChooseUserBinding? = null

    private val pinListener = object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {}
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            if (s != null && this@ChooseUserFragment::viewModel.isInitialized) {
                viewModel.setPinData(s)
                // Automatically submit on 4 digits entered
                if (s.length >= 4) {
                    viewModel.submitPin()
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        ((activity as Activity).application as ChronicleApplication)
            .appComponent
            .inject(this)
        super.onCreate(savedInstanceState)

        val tempBinding = OnboardingPlexChooseUserBinding.inflate(inflater, container, false)
        tempBinding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(
            viewModelStore,
            viewModelFactory
        ).get(ChooseUserViewModel::class.java)

        userListAdapter = UserListAdapter(
            UserClickListener { user ->
                viewModel.pickUser(user)
            }
        )
        tempBinding.userList.adapter = userListAdapter

        tempBinding.pinEdittext.addTextChangedListener(pinListener)

        viewModel.userMessage.observe(viewLifecycleOwner) {
            if (!it.hasBeenHandled) {
                Toast.makeText(requireContext(), it.getContentIfNotHandled(), LENGTH_SHORT).show()
            }
        }

        tempBinding.pinToolbar.setNavigationOnClickListener {
            hidePinEntryScreen()
        }

        tempBinding.pinEdittext.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                viewModel.submitPin()
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }

        viewModel.pinErrorMessage.observe(
            viewLifecycleOwner,
            Observer
            {
                if (!it.isNullOrEmpty()) {
                    tempBinding.pinEdittext.error = it
                } else {
                    tempBinding.pinEdittext.error = null
                }
            }
        )

        tempBinding.viewModel = viewModel
        binding = tempBinding
        return tempBinding.root
    }

    override fun onDestroyView() {
        binding?.pinEdittext?.removeTextChangedListener(pinListener)
        binding?.pinEdittext?.setOnEditorActionListener(null)
        binding?.pinToolbar?.setNavigationOnClickListener(null)
        binding = null

        super.onDestroyView()
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
