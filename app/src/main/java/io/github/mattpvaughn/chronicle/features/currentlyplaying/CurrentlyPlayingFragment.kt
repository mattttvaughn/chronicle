package io.github.mattpvaughn.chronicle.features.currentlyplaying

import android.app.Activity
import android.content.Context
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import io.github.mattpvaughn.chronicle.application.MainActivity
import io.github.mattpvaughn.chronicle.application.MainActivityViewModel
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import io.github.mattpvaughn.chronicle.databinding.FragmentCurrentlyPlayingBinding
import io.github.mattpvaughn.chronicle.features.bookdetails.TrackClickListener
import io.github.mattpvaughn.chronicle.features.bookdetails.TrackListAdapter
import io.github.mattpvaughn.chronicle.features.player.UPDATE_SLEEP_TIMER_STRING
import io.github.mattpvaughn.chronicle.injection.components.ActivityComponent

/**
 *
 */
open class CurrentlyPlayingFragment : Fragment() {

    private lateinit var currentlyPlayingInterface: MainActivity.CurrentlyPlayingInterface

    companion object {
        fun newInstance() = CurrentlyPlayingFragment()
    }

    private val viewModel: CurrentlyPlayingViewModel by lazy {
        ViewModelProvider(
            this,
            viewModelFactory
        ).get(CurrentlyPlayingViewModel::class.java)
    }


    open lateinit var viewModelFactory: CurrentlyPlayingViewModel.Factory

    internal lateinit var activityComponent: ActivityComponent

    override fun onAttach(context: Context) {
        currentlyPlayingInterface = (context as MainActivity).getCurrentlyPlayingInterface()
        injectMembers()
        super.onAttach(context as Context)
    }

    open fun injectMembers() {
        activityComponent = (activity as MainActivity).activityComponent
        viewModelFactory = activityComponent.currentlyPlayingViewModelFactory()
    }

    override fun onStart() {
        super.onStart()

        activity!!
        val context = context!!

        LocalBroadcastManager.getInstance(context).registerReceiver(
            viewModel.onUpdateSleepTimer, IntentFilter(
                UPDATE_SLEEP_TIMER_STRING
            )
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

        binding.viewModel = viewModel
        binding.lifecycleOwner = this@CurrentlyPlayingFragment

        val adapter = TrackListAdapter(object : TrackClickListener {
            override fun onClick(track: MediaItemTrack) {
                viewModel.jumpToTrack(track)
            }
        })
        binding.chapterProgressSeekbar.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    viewModel.seekTo(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}

        })
        binding.tracks.isNestedScrollingEnabled = false
        binding.tracks.adapter = adapter
        binding.detailsToolbar.setNavigationOnClickListener {
            currentlyPlayingInterface.setState(MainActivityViewModel.BottomSheetState.COLLAPSED)
        }

        return binding.root
    }
}


