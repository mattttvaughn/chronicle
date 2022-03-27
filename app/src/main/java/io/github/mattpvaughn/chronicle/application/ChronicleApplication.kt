package io.github.mattpvaughn.chronicle.application

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import com.bumptech.glide.Glide
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.core.ImagePipelineConfig
import io.github.mattpvaughn.chronicle.BuildConfig
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import io.github.mattpvaughn.chronicle.data.model.asServer
import io.github.mattpvaughn.chronicle.data.sources.plex.*
import io.github.mattpvaughn.chronicle.data.sources.plex.model.Connection
import io.github.mattpvaughn.chronicle.injection.components.AppComponent
import io.github.mattpvaughn.chronicle.injection.components.DaggerAppComponent
import io.github.mattpvaughn.chronicle.injection.modules.AppModule
import kotlinx.coroutines.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

// Exposing a ref to the application statically doesn't leak anything because Application is already
// a singleton
@Suppress("LeakingThis")
@Singleton
open class ChronicleApplication : Application() {
    // Instance of the AppComponent that will be used by all the Activities in the project
    val appComponent by lazy {
        initializeComponent()
    }

    init {
        INSTANCE = this
    }

    private var applicationJob = Job()
    private val applicationScope = CoroutineScope(applicationJob + Dispatchers.Main)

    @Inject
    lateinit var plexPrefs: PlexPrefsRepo

    @Inject
    lateinit var plexMediaService: PlexMediaService

    @Inject
    lateinit var plexConfig: PlexConfig

    @Inject
    lateinit var prefsRepo: PrefsRepo

    @Inject
    lateinit var billingManager: ChronicleBillingManager

    @Inject
    lateinit var unhandledExceptionHandler: CoroutineExceptionHandler

    @Inject
    lateinit var cachedFileManager: ICachedFileManager

    @Inject
    lateinit var plexLoginService: PlexLoginService

    @Inject
    lateinit var frescoConfig: ImagePipelineConfig

    override fun onCreate() {
        if (USE_STRICT_MODE && BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
//                    choose which ones you want
//                    .detectDiskReads()
//                    .detectDiskWrites()
//                    .detectNetwork() // or .detectAll() for all detectable problems
                    .penaltyLog()
                    .penaltyDeath()
                    .build()
            )
            StrictMode.setVmPolicy(
                VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .detectActivityLeaks()
                    .penaltyLog()
                    .penaltyDeath()
                    .build()
            )
        }
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        appComponent.inject(this)
        setupNetwork(plexPrefs)
        updateDownloadedFileState()
        super.onCreate()
        Fresco.initialize(this, frescoConfig)
        // TODO: remove in a future version
        applicationScope.launch {
            withContext(Dispatchers.IO) {
                Glide.get(Injector.get().applicationContext()).clearDiskCache()
            }
        }
    }

    /**
     * Updates the book and track repositories to reflect the true state of downloaded files
     */
    private fun updateDownloadedFileState() {
        applicationScope.launch {
            withContext(Dispatchers.IO) {
                cachedFileManager.refreshTrackDownloadedStatus()
            }
        }
    }

    open fun initializeComponent(): AppComponent {
        // We pass the applicationContext that will be used as Context in the graph
        return DaggerAppComponent.builder().appModule(AppModule(this)).build()
    }

    companion object {
        private var INSTANCE: ChronicleApplication? = null

        @JvmStatic
        fun get(): ChronicleApplication = INSTANCE!!
    }

    @OptIn(InternalCoroutinesApi::class)
    private fun setupNetwork(plexPrefs: PlexPrefsRepo) {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(object :
                ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    connectToServer()
                    super.onAvailable(network)
                }

                override fun onLost(network: Network) {
                    // Prevent from running on ConnectivityThread, because onLost is apparently
                    // called on ConnectivityThread with no warning
                    applicationScope.launch {
                        withContext(Dispatchers.Main) {
                            plexConfig.connectionHasBeenLost()
                        }
                    }
                    super.onLost(network)
                }
            })
        } else {
            // network listener for sdk 24 and below
            registerReceiver(networkStateListener, IntentFilter().apply {
                @Suppress("DEPRECATION")
                addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            })
        }
        val server = plexPrefs.server
        if (server != null) {
            plexConfig.setPotentialConnections(server.connections)
            applicationScope.launch(unhandledExceptionHandler) {
                val retrievedConnections: List<Connection> = withTimeoutOrNull(4000L) {
                    try {
                        plexLoginService.resources()
                            .filter { it.provides.contains("server") }
                            .map { it.asServer() }
                            .filter { it.serverId == server.serverId }
                            .flatMap { it.connections }
                    } catch (e: Exception) {
                        Timber.e("Failed to retrieve new connections: $e")
                        emptyList()
                    }
                } ?: emptyList()
                Timber.i("Updated new connections: $retrievedConnections")
                plexPrefs.server = server.copy(
                    connections = server.connections + retrievedConnections
                )
                Timber.i("Retrieved new connections: $retrievedConnections")
                try {
                    Timber.i("Connection to server!")
                    plexConfig.connectToServer(plexMediaService)
                } catch (t: Throwable) {
                    Timber.e("Exception in chooseViableConnections in ChronicleApplication: $t")
                }
            }
        }
    }

    @InternalCoroutinesApi
    private val networkStateListener = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            applicationScope.launch {
                if (context != null && intent != null) {
                    plexConfig.connectionHasBeenLost()
                    connectToServer()
                }
            }
        }
    }

    // Connect to the first connection which can establish a connection
    @InternalCoroutinesApi
    private fun connectToServer() {
        plexConfig.connectToServer(plexMediaService)
    }

    override fun onTrimMemory(level: Int) {
        Fresco.getImagePipeline().clearMemoryCaches()
        super.onTrimMemory(level)
    }

    override fun onLowMemory() {
        Fresco.getImagePipeline().clearMemoryCaches()
        super.onLowMemory()
    }

}
