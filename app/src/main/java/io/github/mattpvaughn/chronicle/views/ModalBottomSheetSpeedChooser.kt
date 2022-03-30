package io.github.mattpvaughn.chronicle.views

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.Slider
import io.github.mattpvaughn.chronicle.application.MainActivity
import io.github.mattpvaughn.chronicle.databinding.ModalBottomSheetSpeedChooserBinding
import io.github.mattpvaughn.chronicle.features.currentlyplaying.CurrentlyPlayingViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import timber.log.Timber
import javax.inject.Inject

@ExperimentalCoroutinesApi
class ModalBottomSheetSpeedChooser : BottomSheetDialogFragment() {

    @Inject
    lateinit var viewModelFactory: CurrentlyPlayingViewModel.Factory

    private val viewModel: CurrentlyPlayingViewModel by lazy {
        ViewModelProvider(this, viewModelFactory).get(CurrentlyPlayingViewModel::class.java)
    }

    override fun onAttach(context: Context) {
        (requireActivity() as MainActivity).activityComponent!!.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val binding = ModalBottomSheetSpeedChooserBinding.inflate(inflater, container, false)
        binding.viewModel = viewModel

        binding.speedSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}

            override fun onStopTrackingTouch(slider: Slider) {
                viewModel.updatePlaybackSpeed(slider.value)
            }
        })

        binding.speedSlider.setLabelFormatter { value: Float ->
            String.format("%.2f", value) + "x"
        }

        binding.speedPresets.setOnCheckedStateChangeListener { group: ChipGroup, checkedId ->
            Timber.w("setOnCheckedStateChangeListener: $group | $checkedId")
            val speed = when (group.findViewById<Chip>(checkedId.first()).tag as String) {
                "1.0x" -> 1.0f
                "1.2x" -> 1.2f
                "1.5x" -> 1.5f
                "2.0x" -> 2.0f
                // R.string.playback_speed_1_0x -> 1.0f  (not sure why this gives an error)
                // R.string.playback_speed_1_2x -> 1.2f
                // R.string.playback_speed_1_5x -> 1.5f
                // R.string.playback_speed_2_0x -> 2.0f
                else -> 1.0f
            }
            viewModel.updatePlaybackSpeed(speed)
            binding.speedSlider.value = speed
        }

        return binding.root

    }

    companion object {
        const val TAG = "ModalBottomSheetSpeedChooser"
    }
}
