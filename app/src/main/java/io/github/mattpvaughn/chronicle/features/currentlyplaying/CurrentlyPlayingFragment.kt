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
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import io.github.mattpvaughn.chronicle.application.MainActivity
import io.github.mattpvaughn.chronicle.application.MainActivityViewModel
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import io.github.mattpvaughn.chronicle.data.plex.PlexConfig
import io.github.mattpvaughn.chronicle.databinding.FragmentCurrentlyPlayingBinding
import io.github.mattpvaughn.chronicle.features.bookdetails.TrackClickListener
import io.github.mattpvaughn.chronicle.features.bookdetails.TrackListAdapter
import io.github.mattpvaughn.chronicle.features.player.UPDATE_SLEEP_TIMER_STRING
import io.github.mattpvaughn.chronicle.util.observeEvent
import javax.inject.Inject

/**
 *
 */
open class CurrentlyPlayingFragment : Fragment() {

    private lateinit var currentlyPlayingInterface: MainActivity.CurrentlyPlayingInterface

    @Inject
    lateinit var plexConfig: PlexConfig

    private lateinit var viewModel: CurrentlyPlayingViewModel

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

        val context = context!!
        LocalBroadcastManager.getInstance(context).registerReceiver(
            viewModel.onUpdateSleepTimer, IntentFilter(UPDATE_SLEEP_TIMER_STRING)
        )
    }

    override fun onStop() {
        LocalBroadcastManager.getInstance(context!!)
            .unregisterReceiver(viewModel.onUpdateSleepTimer)
        super.onStop()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        // Activity and context are non-null on view creation. This informs lint about that
        val binding = FragmentCurrentlyPlayingBinding.inflate(inflater, container, false)

        viewModel = (activity as MainActivity).activityComponent.currentPlayingViewModel()

        viewModel.showUserMessage.observeEvent(viewLifecycleOwner) { message ->
            Toast.makeText(context, message, LENGTH_SHORT).show()
        }

        binding.viewModel = viewModel
        binding.plexConfig = plexConfig
        binding.lifecycleOwner = this@CurrentlyPlayingFragment

        val adapter = TrackListAdapter(object : TrackClickListener {
            override fun onClick(track: MediaItemTrack) {
                viewModel.jumpToTrack(track)
            }
        })
        binding.chapterProgressSeekbar.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            // TODO: only update playback here: by updating continuously in onProgressChanged we
            //       make a lot of DB calls, I think
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (seekBar == null) {
                    return
                }
                viewModel.seekTo(seekBar.progress)
            }

        })
        binding.tracks.isNestedScrollingEnabled = false
        binding.tracks.adapter = adapter
        binding.detailsToolbar.setNavigationOnClickListener {
            currentlyPlayingInterface.setState(MainActivityViewModel.BottomSheetState.COLLAPSED)
        }

        return binding.root
    }
}


