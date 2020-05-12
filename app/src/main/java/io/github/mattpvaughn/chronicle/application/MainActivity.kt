package io.github.mattpvaughn.chronicle.application

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.data.local.IBookRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME
import io.github.mattpvaughn.chronicle.data.plex.PlexConfig
import io.github.mattpvaughn.chronicle.data.plex.PlexPrefsRepo
import io.github.mattpvaughn.chronicle.databinding.ActivityMainBinding
import io.github.mattpvaughn.chronicle.features.currentlyplaying.CurrentlyPlayingFragment
import io.github.mattpvaughn.chronicle.features.player.MediaServiceConnection
import io.github.mattpvaughn.chronicle.features.player.PlaybackErrorHandler.Companion.ACTION_PLAYBACK_ERROR
import io.github.mattpvaughn.chronicle.features.player.PlaybackErrorHandler.Companion.PLAYBACK_ERROR_MESSAGE
import io.github.mattpvaughn.chronicle.injection.components.ActivityComponent
import io.github.mattpvaughn.chronicle.injection.components.DaggerActivityComponent
import io.github.mattpvaughn.chronicle.injection.modules.ActivityModule
import io.github.mattpvaughn.chronicle.injection.scopes.ActivityScope
import io.github.mattpvaughn.chronicle.util.observeEvent
import javax.inject.Inject


@ActivityScope
open class MainActivity : AppCompatActivity() {

    private val viewModel: MainActivityViewModel by lazy {
        ViewModelProvider(this, mainActivityViewModelFactory).get(MainActivityViewModel::class.java)
    }

    private lateinit var localBroadcastManager: LocalBroadcastManager

    @Inject
    lateinit var mainActivityViewModelFactory: MainActivityViewModelFactory

    @Inject
    lateinit var plexPrefsRepo: PlexPrefsRepo

    @Inject
    lateinit var bookRepository: IBookRepository

    @Inject
    lateinit var trackRepository: ITrackRepository

    @Inject
    lateinit var mediaServiceConnection: MediaServiceConnection

    @Inject
    lateinit var plexConfig: PlexConfig

    lateinit var activityComponent: ActivityComponent

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(APP_NAME, "MainActivity onCreate()")
        activityComponent = DaggerActivityComponent.builder()
            .appComponent((application as ChronicleApplication).appComponent)
            .activityModule(ActivityModule(this))
            .build()
        activityComponent.inject(this)

        super.onCreate(savedInstanceState)

        localBroadcastManager = LocalBroadcastManager.getInstance(this)

        val binding =
            DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel
        binding.plexConfig = plexConfig

        binding.currentlyPlayingHandle.setOnClickListener {
            viewModel.onCurrentlyPlayingClicked()
        }

        viewModel.isServerConnected.observe(this, Observer { isConnectedToServer ->
            if (isConnectedToServer) {
                Toast.makeText(this, "Connected to server: ${plexConfig.url}", Toast.LENGTH_SHORT)
                    .show()
            }
        })

        viewModel.errorMessage.observeEvent(this) { errorMessage ->
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
        }

        val navController = findNavController(R.id.fragNavHost)
        navController.addOnDestinationChangedListener { _, _, _ ->
            viewModel.minimizeCurrentlyPlaying()
        }
        binding.bottomNav.setupWithNavController(navController)
        setupCurrentlyPlaying()
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
        Log.i(APP_NAME, "MainActivity onStart()")
        registerReceiver(
            onDownloadComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE).apply {
                addAction(DownloadManager.ACTION_NOTIFICATION_CLICKED)
            }
        )
        localBroadcastManager.registerReceiver(onPlaybackError, IntentFilter(ACTION_PLAYBACK_ERROR))
    }

    override fun onStop() {
        Log.i(APP_NAME, "MainActivity onStop()")
        unregisterReceiver(onDownloadComplete)
        localBroadcastManager.unregisterReceiver(onPlaybackError)
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
                    val errorMessage =
                        intent.getStringExtra(PLAYBACK_ERROR_MESSAGE) ?: "Unknown error"
                    val userMessage = when {
                        errorMessage.contains("404") -> {
                            "Playback error (404): Track not found"
                        }
                        errorMessage.contains("503") -> {
                            "Playback error (503): Server unavailable"
                        }
                        errorMessage.contains("401") -> {
                            "Playback error (401): Not authorized"
                        }
                        else -> errorMessage
                    }
                    viewModel.showUserMessage(userMessage)
                }
                else -> throw NoWhenBranchMatchedException("Unknown playback error")
            }
        }
    }

    companion object {
        const val FLAG_OPEN_ACTIVITY_TO_CURRENTLY_PLAYING = "OPEN_ACTIVITY_TO_AUDIOBOOK"
    }
}
