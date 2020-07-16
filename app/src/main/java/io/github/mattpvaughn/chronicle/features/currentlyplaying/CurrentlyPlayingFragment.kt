package io.github.mattpvaughn.chronicle.features.currentlyplaying

import android.content.Context
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import io.github.mattpvaughn.chronicle.application.MainActivity
import io.github.mattpvaughn.chronicle.application.MainActivityViewModel.BottomSheetState.COLLAPSED
import io.github.mattpvaughn.chronicle.data.model.Chapter
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexConfig
import io.github.mattpvaughn.chronicle.databinding.FragmentCurrentlyPlayingBinding
import io.github.mattpvaughn.chronicle.features.bookdetails.ChapterListAdapter
import io.github.mattpvaughn.chronicle.features.bookdetails.TrackClickListener
import io.github.mattpvaughn.chronicle.features.player.SleepTimer
import io.github.mattpvaughn.chronicle.util.observeEvent
import javax.inject.Inject

/**
 *
 */
class CurrentlyPlayingFragment : Fragment() {

    private lateinit var currentlyPlayingInterface: MainActivity.CurrentlyPlayingInterface

    @Inject
    lateinit var plexConfig: PlexConfig

    @Inject
    lateinit var viewModelFactory: CurrentlyPlayingViewModel.Factory

    @Inject
    lateinit var localBroadcastManager: LocalBroadcastManager

    private val viewModel: CurrentlyPlayingViewModel by lazy {
        ViewModelProvider(this, viewModelFactory).get(CurrentlyPlayingViewModel::class.java)
    }

    companion object {
        fun newInstance() = CurrentlyPlayingFragment()
    }

    override fun onAttach(context: Context) {
        currentlyPlayingInterface = (context as MainActivity).getCurrentlyPlayingInterface()
        context.activityComponent.inject(this)
        super.onAttach(context as Context)
    }

    override fun onStart() {
        super.onStart()
        localBroadcastManager.registerReceiver(
            viewModel.onUpdateSleepTimer, IntentFilter(SleepTimer.ACTION_SLEEP_TIMER_CHANGE)
        )
    }

    override fun onStop() {
        localBroadcastManager.unregisterReceiver(viewModel.onUpdateSleepTimer)
        super.onStop()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        // Activity and context are non-null on view creation. This informs lint about that
        val binding = FragmentCurrentlyPlayingBinding.inflate(inflater, container, false)

        viewModel.showUserMessage.observeEvent(viewLifecycleOwner) { message ->
            Toast.makeText(context, message, LENGTH_SHORT).show()
        }

        binding.viewModel = viewModel
        binding.plexConfig = plexConfig
        binding.lifecycleOwner = viewLifecycleOwner

        val adapter = ChapterListAdapter(object : TrackClickListener {
            override fun onClick(chapter: Chapter) {
                viewModel.jumpToChapter(chapter.startTimeOffset)
            }
        })
        binding.chapterProgressSeekbar.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (seekBar != null) {
                    viewModel.seekTo(seekBar.progress.toDouble() / seekBar.max)
                }
            }
        })

        binding.tracks.adapter = adapter

        binding.detailsToolbar.setNavigationOnClickListener {
            currentlyPlayingInterface.setBottomSheetState(COLLAPSED)
        }

        return binding.root
    }


}


