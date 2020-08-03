package io.github.mattpvaughn.chronicle.application

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.SearchManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.application.MainActivityViewModel.BottomSheetState.COLLAPSED
import io.github.mattpvaughn.chronicle.application.MainActivityViewModel.BottomSheetState.EXPANDED
import io.github.mattpvaughn.chronicle.data.local.IBookRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository
import io.github.mattpvaughn.chronicle.data.sources.plex.IPlexLoginRepo
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexConfig
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexPrefsRepo
import io.github.mattpvaughn.chronicle.databinding.ActivityMainBinding
import io.github.mattpvaughn.chronicle.features.currentlyplaying.CurrentlyPlayingFragment
import io.github.mattpvaughn.chronicle.features.player.MediaServiceConnection
import io.github.mattpvaughn.chronicle.features.player.PlaybackErrorHandler.Companion.ACTION_PLAYBACK_ERROR
import io.github.mattpvaughn.chronicle.features.player.PlaybackErrorHandler.Companion.PLAYBACK_ERROR_MESSAGE
import io.github.mattpvaughn.chronicle.injection.components.ActivityComponent
import io.github.mattpvaughn.chronicle.injection.components.DaggerActivityComponent
import io.github.mattpvaughn.chronicle.injection.modules.ActivityModule
import io.github.mattpvaughn.chronicle.injection.scopes.ActivityScope
import io.github.mattpvaughn.chronicle.navigation.Navigator
import io.github.mattpvaughn.chronicle.util.observeEvent
import timber.log.Timber
import javax.inject.Inject


@ActivityScope
open class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var localBroadcastManager: LocalBroadcastManager

    @Inject
    lateinit var mainActivityViewModelFactory: MainActivityViewModel.Factory

    private val viewModel: MainActivityViewModel by lazy {
        ViewModelProvider(this, mainActivityViewModelFactory).get(MainActivityViewModel::class.java)
    }

    @Inject
    lateinit var plexLoginRepo: IPlexLoginRepo

    @Inject
    lateinit var navigator: Navigator

    @Inject
    lateinit var plexPrefsRepo: PlexPrefsRepo

    @Inject
    lateinit var bookRepository: IBookRepository

    @Inject
    lateinit var trackRepository: ITrackRepository

    @Inject
    lateinit var plexConfig: PlexConfig

    @Inject
    lateinit var mediaServiceConnection: MediaServiceConnection

    lateinit var activityComponent: ActivityComponent

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.i("MainActivity onCreate()")
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

        viewModel.errorMessage.observeEvent(this) { errorMessage ->
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
        }

        binding.bottomNav.setOnNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.nav_settings -> {
                    navigator.showSettings()
                }
                R.id.nav_library -> {
                    navigator.showLibrary()
                }
                R.id.nav_home -> {
                    navigator.showHome()
                }
                else -> throw NoWhenBranchMatchedException("Unknown bottom tab id: ${it.itemId}")
            }
            viewModel.minimizeCurrentlyPlaying()
            return@setOnNavigationItemSelectedListener true
        }

        if (savedInstanceState == null) {
            setupCurrentlyPlaying()
        }

        // If the app is being launched by voice assistant with a query
        val query = intent.getStringExtra(SearchManager.QUERY)
        if (!query.isNullOrEmpty()) {
            mediaServiceConnection.connect {
                mediaServiceConnection.transportControls?.playFromSearch(query, Bundle())
            }
        }

        handleNotificationIntent(intent)
    }

    override fun onBackPressed() {
        // If currently playing view is over fragments, close it via back button
        if (viewModel.currentlyPlayingLayoutState.value == EXPANDED) {
            viewModel.setBottomSheetState(COLLAPSED)
            return
        }
        // default to activity back stack if navigator did not handle anything
        if (!navigator.onBackPressed()) {
            Timber.i("MainActivity super.onBackPressed()")
            super.onBackPressed()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupCurrentlyPlaying() {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(
            R.id.currently_playing_fragment_container,
            CurrentlyPlayingFragment.newInstance()
        )
        transaction.commit()
        val handle = findViewById<View>(R.id.currently_playing_handle)
        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent?,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (distanceY > distanceX) {
                    viewModel.onCurrentlyPlayingHandleDragged()
                }
                return super.onScroll(e1, e2, distanceX, distanceY)
            }
        })
        handle.setOnTouchListener { v, event ->
            gd.onTouchEvent(event)
            v.onTouchEvent(event)
        }
    }

    interface CurrentlyPlayingInterface {
        fun setBottomSheetState(state: MainActivityViewModel.BottomSheetState)
    }

    fun getCurrentlyPlayingInterface(): CurrentlyPlayingInterface {
        return viewModel
    }

    override fun onStart() {
        super.onStart()
        Timber.i("MainActivity onStart()")
        registerReceiver(
            onDownloadComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE).apply {
                addAction(DownloadManager.ACTION_NOTIFICATION_CLICKED)
            }
        )
        localBroadcastManager.registerReceiver(onPlaybackError, IntentFilter(ACTION_PLAYBACK_ERROR))
    }

    override fun onStop() {
        Timber.i("MainActivity onStop()")
        unregisterReceiver(onDownloadComplete)
        localBroadcastManager.unregisterReceiver(onPlaybackError)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent?) {
        handleNotificationIntent(intent)
        super.onNewIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val isIntentFromNotification = intent?.extras?.getBoolean(
            FLAG_OPEN_ACTIVITY_TO_CURRENTLY_PLAYING, false
        ) == true
        Timber.i("Should the bottom sheet be maximized? $isIntentFromNotification")
        if (isIntentFromNotification) {
            viewModel.maximizeCurrentlyPlaying()
        }
    }

    private val onDownloadComplete = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                DownloadManager.ACTION_DOWNLOAD_COMPLETE -> {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    viewModel.handleDownloadedTrack(id)
                }
                DownloadManager.ACTION_NOTIFICATION_CLICKED -> {
                    Timber.i("Clicked notification!")
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
