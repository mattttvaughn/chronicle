package io.github.mattpvaughn.chronicle.features.bookdetails

import android.app.DownloadManager
import android.content.ComponentName
import android.content.Context.DOWNLOAD_SERVICE
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.work.WorkManager
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.application.MainActivity
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import io.github.mattpvaughn.chronicle.data.plex.CachedFileManager
import io.github.mattpvaughn.chronicle.databinding.FragmentAudiobookDetailsBinding
import io.github.mattpvaughn.chronicle.features.player.MediaPlayerService
import io.github.mattpvaughn.chronicle.features.player.MediaServiceConnection
import io.github.mattpvaughn.chronicle.features.player.SimpleProgressUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

class AudiobookDetailsFragment : Fragment() {

    companion object {
        fun newInstance() = AudiobookDetailsFragment()
    }

    val args: AudiobookDetailsFragmentArgs by navArgs()

    private lateinit var viewModel: AudiobookDetailsViewModel
    override fun onStart() {
        super.onStart()

        val activity = activity!!
        val context = context!!
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        retainInstance = true

        // Activity and context are always non-null on view creation. This informs lint about that
        val activity = activity!!
        val context = context!!

        val binding = FragmentAudiobookDetailsBinding.inflate(inflater, container, false)

        val plexPrefsRepo = Injector.get().plexPrefs()
        val prefsRepo = Injector.get().prefsRepo()

        val trackRepository = Injector.get().trackRepo()
        val bookRepository = Injector.get().bookRepo()
        val mediaServiceConnection = (activity as MainActivity).activityComponent.mediaServiceConnection()

        viewModel = AudiobookDetailsViewModel(
            bookRepository = bookRepository,
            trackRepository = trackRepository,
            cachedFileManager = CachedFileManager(
                downloadManager = context.getSystemService(DOWNLOAD_SERVICE) as DownloadManager,
                prefsRepo = prefsRepo,
                coroutineScope = coroutineScope,
                trackRepository = trackRepository,
                bookRepository = bookRepository,
                externalFileDirs = Injector.get().externalDeviceDirs()
            ),
            inputAudiobook = Audiobook(id = args.audiobookId, isCached = args.isAudiobookCached),
            plexPrefsRepo = plexPrefsRepo,
            mediaServiceConnection = mediaServiceConnection,
            progressUpdater = SimpleProgressUpdater(
                serviceScope = coroutineScope,
                trackRepository = trackRepository,
                bookRepository = bookRepository,
                workManager = WorkManager.getInstance(context),
                mediaServiceConnection = mediaServiceConnection,
                prefsRepo = prefsRepo
            )
        )
        binding.viewModel = viewModel
        binding.lifecycleOwner = this@AudiobookDetailsFragment

        val adapter = TrackListAdapter(object : TrackClickListener {
            override fun onClick(track: MediaItemTrack) {
                viewModel.jumpToTrack(track)
            }
        })
        binding.tracks.isNestedScrollingEnabled = false
        binding.tracks.adapter = adapter
        binding.detailsToolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_cast -> Toast.makeText(
                    activity,
                    "Casting not supported yet",
                    Toast.LENGTH_SHORT
                ).show()
                else -> throw IllegalStateException("Unknown menu item selected: $item")
            }
            true
        }

        binding.detailsToolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }


        return binding.root
    }

    private var fragmentJob = Job()
    private val coroutineScope = CoroutineScope(fragmentJob + Dispatchers.Main)

    override fun onDetach() {
        fragmentJob.cancel()
        super.onDetach()
    }


}
