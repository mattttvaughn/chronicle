package io.github.mattpvaughn.chronicle.features.settings

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import io.github.mattpvaughn.chronicle.application.ChronicleApplication
import io.github.mattpvaughn.chronicle.application.ChronicleBillingManager
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME
import io.github.mattpvaughn.chronicle.data.plex.CachedFileManager
import io.github.mattpvaughn.chronicle.databinding.FragmentSettingsBinding
import io.github.mattpvaughn.chronicle.features.login.OnboardingActivity
import io.github.mattpvaughn.chronicle.util.observeEvent
import javax.inject.Inject


class SettingsFragment : Fragment() {

    private lateinit var viewModel: SettingsViewModel

    @Inject
    lateinit var chronicleBillingManager: ChronicleBillingManager

    companion object {
        @JvmStatic
        fun newInstance() = SettingsFragment()
    }

    override fun onAttach(context: Context) {
        ((context as AppCompatActivity).application as ChronicleApplication)
            .appComponent
            .inject(this)
        super.onAttach(context as Context)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.i(APP_NAME, "Settings fragment onCreateView")

        val context = context!!
        val activity = activity!!

        val binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val prefsRepo = Injector.get().prefsRepo()
        val plexPrefsRepo = Injector.get().plexPrefs()
        val trackRepository = Injector.get().trackRepo()
        val bookRepository = Injector.get().bookRepo()
        val plexConfig = Injector.get().plexConfig()
        viewModel = SettingsViewModel(
            trackRepository = trackRepository,
            bookRepository = bookRepository,
            prefsRepo = prefsRepo,
            plexPrefsRepo = plexPrefsRepo,
            cachedFileManager = CachedFileManager(
                context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager,
                prefsRepo,
                lifecycleScope,
                trackRepository,
                bookRepository,
                plexConfig
            ),
            plexConfig = plexConfig
        )
        binding.viewModel = viewModel
        binding.lifecycleOwner = this

        viewModel.messageForUser.observeEvent(viewLifecycleOwner) {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }

        viewModel.upgradeToPremium.observeEvent(viewLifecycleOwner) {
            Log.i(APP_NAME, chronicleBillingManager.billingClient.toString())
            chronicleBillingManager.launchBillingFlow(activity)
        }

        viewModel.webLink.observe(viewLifecycleOwner, Observer {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
        })

        viewModel.showLicenseActivity.observe(viewLifecycleOwner, Observer {
            if (it) {
                startActivity(Intent(context, OssLicensesMenuActivity::class.java))
                viewModel.setShowLicenseActivity(false)
            }
        })

        viewModel.returnToLogin.observe(viewLifecycleOwner, Observer {
            if (it) {
                val intentNoReturn = Intent(activity, OnboardingActivity::class.java)
                intentNoReturn.flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intentNoReturn)
            }
        })

        return binding.root
    }
}
