package io.github.mattpvaughn.chronicle.features.login

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.browser.customtabs.CustomTabsIntent
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.application.MainActivity
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import io.github.mattpvaughn.chronicle.data.sources.MediaSource
import io.github.mattpvaughn.chronicle.data.sources.MediaSourceFactory
import io.github.mattpvaughn.chronicle.data.sources.SourceManager
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexLibrarySource
import io.github.mattpvaughn.chronicle.data.sources.plex.model.OAuthResponse
import io.github.mattpvaughn.chronicle.databinding.OnboardingAddSourceBinding
import io.github.mattpvaughn.chronicle.injection.components.AppComponent.Companion.USER_AGENT
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named

/** Fragment responsible for starting the process of adding a new [MediaSource] */
class AddSourceFragment : Fragment() {

    companion object {
        @JvmStatic
        fun newInstance() = AddSourceFragment()
        const val TAG: String = "Login tag"
    }

    @Inject
    lateinit var prefsRepo: PrefsRepo

    @Inject
    lateinit var viewModelFactory: AddSourceViewModel.Factory

    @Inject
    lateinit var sourceManager: SourceManager

    @Inject
    lateinit var mediaSourceFactory: MediaSourceFactory


    @Inject
    @Named(USER_AGENT)
    lateinit var userAgent: String


    private lateinit var addSourceViewModel: AddSourceViewModel

    override fun onAttach(context: Context) {
        (requireActivity() as MainActivity).activityComponent!!.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val potentialPlexSource = mediaSourceFactory.create(
            sourceManager.generateUniqueId(),
            PlexLibrarySource.TAG
        ) as PlexLibrarySource
        viewModelFactory.potentialPlexSource = potentialPlexSource

        addSourceViewModel = ViewModelProvider(
            this,
            viewModelFactory
        ).get(AddSourceViewModel::class.java)

        val binding = OnboardingAddSourceBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = addSourceViewModel

        addSourceViewModel.authEvent.observe(viewLifecycleOwner, Observer { authRequestEvent ->
            val oAuthPin = authRequestEvent.getContentIfNotHandled()
            if (oAuthPin != null) {
                showPlexLoginWindow(oAuthPin)
            }
        })

        return binding.root
    }

    private fun showPlexLoginWindow(oAuthPin: OAuthResponse) {
        val backButton =
            resources.getDrawable(R.drawable.ic_arrow_back_white, requireActivity().theme)
                .apply { setTint(Color.BLACK) }
        val backButtonBitmap: Bitmap? =
            if (backButton is BitmapDrawable) backButton.bitmap else null

        @Suppress("DEPRECATION")
        val customTabsIntentBuilder =
            CustomTabsIntent.Builder()
                .setToolbarColor(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        resources.getColor(R.color.colorPrimary, requireActivity().theme)
                    } else {
                        resources.getColor(R.color.colorPrimary)
                    }
                )
                .setShowTitle(true)

        if (backButtonBitmap != null) {
            customTabsIntentBuilder.setCloseButtonIcon(backButtonBitmap)
        }

        val customTabsIntent = customTabsIntentBuilder.build()

        // make login url
        val url = addSourceViewModel.makePlexOAuthLoginUrl(oAuthPin.clientIdentifier, oAuthPin.code)

        addSourceViewModel.setLaunched(true)
        customTabsIntent.launchUrl(requireContext(), url)
    }

    override fun onResume() {
        Timber.i("RESUMING LoginFragment")
        addSourceViewModel.checkForAccess()
        super.onResume()
    }
}