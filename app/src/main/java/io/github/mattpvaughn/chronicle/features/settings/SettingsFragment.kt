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
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME
import io.github.mattpvaughn.chronicle.data.plex.CachedFileManager
import io.github.mattpvaughn.chronicle.databinding.FragmentSettingsBinding
import io.github.mattpvaughn.chronicle.features.login.LoginActivity


class SettingsFragment : Fragment() {

    private lateinit var viewModel: SettingsViewModel

    companion object {
        fun newInstance() = SettingsFragment()
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
        val externalFileDirs = ContextCompat.getExternalFilesDirs(context, null).toList()
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
                externalFileDirs
            )
        )
        binding.viewModel = viewModel
        binding.lifecycleOwner = this

        viewModel.messageForUser.observe(viewLifecycleOwner, Observer {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        })

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
                val intentNoReturn = Intent(activity, LoginActivity::class.java)
                intentNoReturn.flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intentNoReturn)
            }
        })

        return binding.root
    }
}
