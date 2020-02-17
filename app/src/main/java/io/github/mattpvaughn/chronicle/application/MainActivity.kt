package io.github.mattpvaughn.chronicle.application

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME
import io.github.mattpvaughn.chronicle.databinding.ActivityMainBinding
import io.github.mattpvaughn.chronicle.features.currentlyplaying.CurrentlyPlayingFragment
import io.github.mattpvaughn.chronicle.features.player.MediaPlayerService.PlaybackErrorHandler.Companion.ACTION_PLAYBACK_ERROR
import io.github.mattpvaughn.chronicle.features.player.MediaPlayerService.PlaybackErrorHandler.Companion.PLAYBACK_ERROR_MESSAGE
import io.github.mattpvaughn.chronicle.features.player.MediaPlayerService.PlaybackErrorHandler.Companion.PLAYBACK_ERROR_TYPE
import io.github.mattpvaughn.chronicle.injection.components.ActivityComponent
import io.github.mattpvaughn.chronicle.injection.components.DaggerActivityComponent
import io.github.mattpvaughn.chronicle.injection.modules.ActivityModule
import io.github.mattpvaughn.chronicle.injection.scopes.PerActivity
import javax.inject.Inject


@PerActivity
open class MainActivity : AppCompatActivity() {

    private val viewModel : MainActivityViewModel by lazy {
        ViewModelProvider(this, mainActivityViewModelFactory).get(MainActivityViewModel::class.java)
    }

    @Inject
    lateinit var mainActivityViewModelFactory: MainActivityViewModelFactory

    lateinit var activityComponent: ActivityComponent

    override fun onCreate(savedInstanceState: Bundle?) {
        provideActivityComponent()
        activityComponent.inject(this)
        activityComponent.mediaServiceConnection().connect()

        super.onCreate(savedInstanceState)

        if (intent != null) {
            // Opened from notification! Get active audiobook!
            val openCurrentlyPlaying =
                intent.getBooleanExtra(FLAG_OPEN_ACTIVITY_TO_CURRENTLY_PLAYING, false)
            Log.i(APP_NAME, "Got launch notification! Audiobook = $openCurrentlyPlaying")
        }

        val binding = DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel

        binding.currentlyPlayingHandle.setOnClickListener {
            viewModel.onCurrentlyPlayingClicked()
        }

        val navController = findNavController(R.id.fragNavHost)
        navController.addOnDestinationChangedListener { _, _, _ ->
            viewModel.minimizeCurrentlyPlaying()
        }
        binding.bottomNav.setupWithNavController(navController)

        setupCurrentlyPlaying()
    }

    open fun provideActivityComponent() : ActivityComponent {
        return DaggerActivityComponent.builder()
            .appComponent((application as ChronicleApplication).appComponent)
            .activityModule(ActivityModule(this))
            .build()
    }

    private fun setupCurrentlyPlaying() {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(
            R.id.currently_playing_fragment_container,
            CurrentlyPlayingFragment.newInstance()
        )
        transaction.commit()
    }

    interface CurrentlyPlayingInterface {
        fun setState(state: MainActivityViewModel.BottomSheetState)
    }

    fun getCurrentlyPlayingInterface(): CurrentlyPlayingInterface {
        return viewModel
    }


    override fun onStart() {
        super.onStart()
        registerReceiver(
            onDownloadComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE).apply {
                addAction(DownloadManager.ACTION_NOTIFICATION_CLICKED)
            }
        )
        registerReceiver(onPlaybackError, IntentFilter(ACTION_PLAYBACK_ERROR))
    }

    override fun onStop() {
        activityComponent.mediaServiceConnection().disconnect()
        unregisterReceiver(onDownloadComplete)
        unregisterReceiver(onPlaybackError)
        super.onStop()
    }

    private val onDownloadComplete = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            //Fetching the download id received with the broadcast
            when (intent.action) {
                DownloadManager.ACTION_DOWNLOAD_COMPLETE -> {
                    val downloadManager =
                        getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    viewModel.handleDownloadedTrack(downloadManager, id)
                }
                DownloadManager.ACTION_NOTIFICATION_CLICKED -> {
                    Log.i(APP_NAME, "Clicked notification!")
                    throw NotImplementedError("No action implemented yet for download notification click")
                }
            }
        }
    }

    private val onPlaybackError = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            //Fetching the download id received with the broadcast
            when (intent.action) {
                ACTION_PLAYBACK_ERROR -> {
                    val errorMessage = intent.getStringExtra(PLAYBACK_ERROR_MESSAGE)
                    val errorType = intent.getIntExtra(PLAYBACK_ERROR_TYPE, -1)
                    viewModel.showUserMessage(errorMessage)
                }
                else -> throw NoWhenBranchMatchedException("Unknown playback error")
            }
        }
    }

    companion object {
        const val FLAG_OPEN_ACTIVITY_TO_CURRENTLY_PLAYING = "OPEN_ACTIVITY_TO_AUDIOBOOK"
    }
}
