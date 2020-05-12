package io.github.mattpvaughn.chronicle.features.bookdetails

import android.app.DownloadManager
import android.content.Context
import android.content.Context.DOWNLOAD_SERVICE
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.application.ChronicleApplication
import io.github.mattpvaughn.chronicle.application.MainActivity
import io.github.mattpvaughn.chronicle.data.local.IBookRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME
import io.github.mattpvaughn.chronicle.data.plex.CachedFileManager
import io.github.mattpvaughn.chronicle.data.plex.PlexConfig
import io.github.mattpvaughn.chronicle.databinding.FragmentAudiobookDetailsBinding
import io.github.mattpvaughn.chronicle.features.player.MediaServiceConnection
import io.github.mattpvaughn.chronicle.util.observeEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import javax.inject.Inject

class AudiobookDetailsFragment : Fragment() {

    companion object {
        fun newInstance() = AudiobookDetailsFragment()
    }

    private val args: AudiobookDetailsFragmentArgs by navArgs()

    @Inject
    lateinit var mediaServiceConnection: MediaServiceConnection

    @Inject
    lateinit var prefsRepo: PrefsRepo

    @Inject
    lateinit var trackRepository: ITrackRepository

    @Inject
    lateinit var bookRepository: IBookRepository

    @Inject
    lateinit var plexConfig: PlexConfig

    private lateinit var viewModel: AudiobookDetailsViewModel
    override fun onAttach(context: Context) {
        (activity!!.application as ChronicleApplication).appComponent.inject(this)
        Log.i(APP_NAME, "AudiobookDetailsFragment onAttach()")
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.i(APP_NAME, "AudiobookDetailsFragment onCreateView()")

        retainInstance = true

        // Activity and context are always non-null on view creation. This informs lint about that
        val context = context!!

        val binding = FragmentAudiobookDetailsBinding.inflate(inflater, container, false)

        Log.i(
            APP_NAME,
            "AudiobookDetailsFragment mediaServiceConnection isConnected? ${mediaServiceConnection.isConnected.value}"
        )

        viewModel = AudiobookDetailsViewModel(
            bookRepository = bookRepository,
            trackRepository = trackRepository,
            cachedFileManager = CachedFileManager(
                downloadManager = context.getSystemService(DOWNLOAD_SERVICE) as DownloadManager,
                prefsRepo = prefsRepo,
                coroutineScope = coroutineScope,
                trackRepository = trackRepository,
                bookRepository = bookRepository,
                plexConfig = plexConfig
            ),
            inputAudiobook = Audiobook(id = args.audiobookId, isCached = args.isAudiobookCached),
            mediaServiceConnection = mediaServiceConnection,
            progressUpdater = (activity as MainActivity).activityComponent.progressUpdater(),
            plexConfig = plexConfig,
            prefsRepo = prefsRepo
        )
        binding.viewModel = viewModel
        binding.lifecycleOwner = this@AudiobookDetailsFragment
        binding.plexConfig = plexConfig

        val adapter = TrackListAdapter(object : TrackClickListener {
            override fun onClick(track: MediaItemTrack) {
                viewModel.jumpToTrack(track)
            }
        })
        viewModel.messageForUser.observeEvent(viewLifecycleOwner) { message ->
            Toast.makeText(context, message, LENGTH_SHORT).show()
        }
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
        Log.i(APP_NAME, "AudiobookDetailsFragment onDetach()")
        fragmentJob.cancel()
        super.onDetach()
    }


}
