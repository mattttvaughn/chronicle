package io.github.mattpvaughn.chronicle.features.bookdetails

import android.content.Context
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Bundle
import android.view.*
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.application.MainActivity
import io.github.mattpvaughn.chronicle.data.local.IBookRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.model.Chapter
import io.github.mattpvaughn.chronicle.data.sources.MediaSource
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexConfig
import io.github.mattpvaughn.chronicle.databinding.FragmentAudiobookDetailsBinding
import io.github.mattpvaughn.chronicle.features.player.MediaServiceConnection
import io.github.mattpvaughn.chronicle.navigation.Navigator
import io.github.mattpvaughn.chronicle.util.observeEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import timber.log.Timber
import javax.inject.Inject

@ExperimentalCoroutinesApi
class AudiobookDetailsFragment : Fragment() {

    companion object {
        fun newInstance() = AudiobookDetailsFragment()
        const val TAG = "details tag"
        const val ARG_AUDIOBOOK_ID = "audiobook_id"
        const val ARG_AUDIOBOOK_TITLE = "ARG_AUDIOBOOK_TITLE"
        const val ARG_IS_AUDIOBOOK_CACHED = "is_audiobook_cached"
    }

    @Inject
    lateinit var prefsRepo: PrefsRepo

    @Inject
    lateinit var navigator: Navigator

    @Inject
    lateinit var trackRepository: ITrackRepository

    @Inject
    lateinit var bookRepository: IBookRepository

    @Inject
    lateinit var plexConfig: PlexConfig

    @Inject
    lateinit var mediaServiceConnection: MediaServiceConnection

    @Inject
    lateinit var viewModelFactory: AudiobookDetailsViewModel.Factory

    lateinit var viewModel: AudiobookDetailsViewModel

    override fun onAttach(context: Context) {
        (requireActivity() as MainActivity).activityComponent!!.inject(this)
        Timber.i("AudiobookDetailsFragment onAttach()")
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Timber.i("AudiobookDetailsFragment onCreateView()")

        val binding = FragmentAudiobookDetailsBinding.inflate(inflater, container, false)

        val inputId = requireArguments().getInt(ARG_AUDIOBOOK_ID)
        val bookTitle = requireArguments().getString(ARG_AUDIOBOOK_TITLE) ?: ""
        val inputCached = requireArguments().getBoolean(ARG_IS_AUDIOBOOK_CACHED)

        viewModelFactory.inputAudiobook = Audiobook(
            id = inputId,
            title = bookTitle,
            source = MediaSource.NO_SOURCE_FOUND,
            isCached = inputCached
        )
        viewModel =
            ViewModelProvider(this, viewModelFactory).get(AudiobookDetailsViewModel::class.java)

        binding.viewModel = viewModel
        binding.lifecycleOwner = viewLifecycleOwner
        binding.plexConfig = plexConfig

        val adapter = ChapterListAdapter(object : TrackClickListener {
            override fun onClick(chapter: Chapter) {
                Timber.i("Starting chapter with name: ${chapter.title}")
                viewModel.jumpToChapter(offset = chapter.startTimeOffset, trackId = chapter.trackId)
            }
        })
        binding.tracks.adapter = adapter

        // TODO casting
//        val menu = binding.detailsToolbar.menu
//        val mediaRouteButton = menu.findItem(R.id.media_route_menu_item).actionView
//
//        if (castContext.castState != CastState.NO_DEVICES_AVAILABLE) {
//            mediaRouteButton.visibility = View.VISIBLE
//        }
//
//        castContext.addCastStateListener { state ->
//            if (state == CastState.NO_DEVICES_AVAILABLE) {
//                mediaRouteButton.visibility = View.GONE
//            } else {
//                if (mediaRouteButton.visibility == View.GONE) {
//                    mediaRouteButton.visibility = View.VISIBLE
//                }
//            }
//        }

        (activity as AppCompatActivity).setSupportActionBar(binding.detailsToolbar)
        binding.detailsToolbar.title = null

        binding.detailsToolbar.setOnMenuItemClickListener {
            when (it.itemId) {
//                R.id.menu_cast -> {
//                    viewModel.pausePlayButtonClicked()
//                    true
//                }
                R.id.toggle_watched -> {
                    viewModel.toggleWatched()
                    true
                }
                R.id.force_sync -> {
                    viewModel.forceSyncBook(hasUserConfirmation = false)
                    true
                }
                else -> super.onOptionsItemSelected(it)
            }
        }

        binding.detailsToolbar.setNavigationOnClickListener {
            requireActivity().onBackPressed()
        }

        viewModel.messageForUser.observeEvent(viewLifecycleOwner) { message ->
            Toast.makeText(context, message.format(resources), LENGTH_SHORT).show()
        }

        viewModel.activeChapter.observe(viewLifecycleOwner) { chapter ->
            Timber.i("Updating current chapter: (${chapter.trackId}, ${chapter.discNumber}, ${chapter.index})")
            adapter.updateCurrentChapter(
                trackId = chapter.trackId,
                discNumber = chapter.discNumber,
                chapterIndex = chapter.index
            )
        }

        viewModel.forceSyncInProgress.observe(viewLifecycleOwner) { isSyncing ->
            val syncMenuItem = binding.detailsToolbar.menu.findItem(R.id.force_sync)
            val syncIcon = syncMenuItem.icon
            if (syncIcon is AnimatedVectorDrawable) {
                if (isSyncing) syncIcon.start() else syncIcon.stop()
            }
        }

        viewModel.isWatchedIcon.observe(viewLifecycleOwner) { icon ->
            Timber.d("isWatchedIcon.observe called")
            binding.detailsToolbar.menu.findItem(R.id.toggle_watched).setIcon(icon)
        }

        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.audiobook_details_menu, menu)
    }
}
