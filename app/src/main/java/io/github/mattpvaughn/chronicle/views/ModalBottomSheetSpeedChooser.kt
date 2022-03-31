package io.github.mattpvaughn.chronicle.views

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.Slider
import io.github.mattpvaughn.chronicle.application.MainActivity
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import io.github.mattpvaughn.chronicle.databinding.ModalBottomSheetSpeedChooserBinding
import kotlinx.coroutines.ExperimentalCoroutinesApi
import timber.log.Timber
import javax.inject.Inject

@ExperimentalCoroutinesApi
class ModalBottomSheetSpeedChooser : BottomSheetDialogFragment() {

    private var prefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    @Inject
    lateinit var prefs: PrefsRepo

    override fun onAttach(context: Context) {
        (requireActivity() as MainActivity).activityComponent!!.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val binding = ModalBottomSheetSpeedChooserBinding.inflate(inflater, container, false)
        binding.speed = prefs.playbackSpeed

        binding.speedSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}

            override fun onStopTrackingTouch(slider: Slider) {
                prefs.playbackSpeed = slider.value
            }
        })

        binding.speedSlider.setLabelFormatter { value: Float ->
            String.format("%.2f", value) + "x"
        }

        binding.speedPresets.setOnCheckedStateChangeListener { group: ChipGroup, checkedId ->
            Timber.w("setOnCheckedStateChangeListener: $group | $checkedId")
            val speed = when (group.findViewById<Chip>(checkedId.first()).tag as String) {
                // Note: tag="@string/xxx" ends up turning into a string and
                // can't be reference as an R.id.xxx
                "1.0x" -> 1.0f
                "1.2x" -> 1.2f
                "1.5x" -> 1.5f
                "2.0x" -> 2.0f
                else -> 1.0f
            }
            prefs.playbackSpeed = speed
            binding.speedSlider.value = speed
        }

        prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == PrefsRepo.KEY_PLAYBACK_SPEED) {
                binding.speed = prefs.playbackSpeed
            }
        }.apply {
            prefs.registerPrefsListener(this)
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        prefsListener?.let {
            prefs.unregisterPrefsListener(it)
        }
    }

    companion object {
        const val TAG = "ModalBottomSheetSpeedChooser"
    }
}
