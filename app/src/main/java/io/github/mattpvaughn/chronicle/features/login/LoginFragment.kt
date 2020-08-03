package io.github.mattpvaughn.chronicle.features.login

import android.app.Activity
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
import io.github.mattpvaughn.chronicle.application.ChronicleApplication
import io.github.mattpvaughn.chronicle.application.FEATURE_FLAG_IS_AUTO_ENABLED
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import io.github.mattpvaughn.chronicle.databinding.OnboardingLoginBinding
import timber.log.Timber
import javax.inject.Inject


class LoginFragment : Fragment() {

    companion object {
        @JvmStatic
        fun newInstance() = LoginFragment()

        const val TAG: String = "Login tag"
    }

    @Inject
    lateinit var prefsRepo: PrefsRepo

    @Inject
    lateinit var viewModelFactory: LoginViewModel.Factory

    private lateinit var loginViewModel: LoginViewModel

    override fun onAttach(context: Context) {
        ((activity as Activity).application as ChronicleApplication)
            .appComponent
            .inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        loginViewModel = ViewModelProvider(
            this,
            viewModelFactory
        ).get(LoginViewModel::class.java)

        val binding = OnboardingLoginBinding.inflate(inflater, container, false)

        binding.enableAuto.visibility =
            if (FEATURE_FLAG_IS_AUTO_ENABLED) View.VISIBLE else View.GONE

        loginViewModel.isLoading.observe(viewLifecycleOwner, Observer { isLoading ->
            if (isLoading) {
                binding.loading.visibility = View.VISIBLE
            } else {
                binding.loading.visibility = View.GONE
            }
        })

        binding.oauthLogin.setOnClickListener {
            loginViewModel.loginWithOAuth()
        }

        binding.enableAuto.isChecked = prefsRepo.allowAuto

        binding.enableAuto.setOnCheckedChangeListener { _, isChecked ->
            prefsRepo.allowAuto = isChecked
        }

        loginViewModel.authEvent.observe(viewLifecycleOwner, Observer { authRequestEvent ->
            val oAuthPin = authRequestEvent.getContentIfNotHandled()
            if (oAuthPin != null) {
                val backButton =
                    resources.getDrawable(R.drawable.ic_arrow_back_white, requireActivity().theme)
                        .apply { setTint(Color.BLACK) }
                val backButtonBitmap: Bitmap? =
                    if (backButton is BitmapDrawable) backButton.bitmap else null

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
                val url = loginViewModel.makeOAuthLoginUrl(oAuthPin.clientIdentifier, oAuthPin.code)

                loginViewModel.setLaunched(true)
                customTabsIntent.launchUrl(requireContext(), url)
            }
        })

        return binding.root
    }

    override fun onResume() {
        Timber.i("RESUMING LoginFragment")
        loginViewModel.checkForAccess()
        super.onResume()
    }
}