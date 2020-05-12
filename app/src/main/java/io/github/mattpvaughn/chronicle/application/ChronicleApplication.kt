package io.github.mattpvaughn.chronicle.application

import android.app.Application
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import android.util.Log
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode.OK
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import io.github.mattpvaughn.chronicle.BuildConfig
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME
import io.github.mattpvaughn.chronicle.data.plex.PlexConfig
import io.github.mattpvaughn.chronicle.data.plex.PlexConnectionChooser
import io.github.mattpvaughn.chronicle.data.plex.PlexConnectionChooser.ConnectionResult.Failure
import io.github.mattpvaughn.chronicle.data.plex.PlexPrefsRepo
import io.github.mattpvaughn.chronicle.injection.components.AppComponent
import io.github.mattpvaughn.chronicle.injection.components.DaggerAppComponent
import io.github.mattpvaughn.chronicle.injection.modules.AppModule
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton


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
    lateinit var plexConfig: PlexConfig

    @Inject
    lateinit var plexConnectionChooser: PlexConnectionChooser

    // Hmmm...
    @Inject
    lateinit var billingManager: ChronicleBillingManager

    @Inject
    lateinit var billingClient: BillingClient

    override fun onCreate() {
        if (STRICT_MODE && BuildConfig.DEBUG) {
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

        appComponent.mediaServiceConnection().connect()
        appComponent.inject(this)
        setupNetwork(plexPrefs)
        setupBilling()
        super.onCreate()
    }

    private fun setupBilling() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == OK) {
                    Log.i(APP_NAME, "Billing client setup successful: $billingClient")
                    billingManager.billingClient = billingClient
                } else {
                    Log.w(APP_NAME, "Billing client setup failed!")
                }
            }

            override fun onBillingServiceDisconnected() {
                // Try to restart the connection on the next request to
                // Google Play by calling the startConnection() method.
                billingClient.startConnection(this)
            }
        })
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

    @UseExperimental(InternalCoroutinesApi::class)
    private fun setupNetwork(plexPrefs: PlexPrefsRepo) {
        val plexAuthToken = plexPrefs.getAuthToken()
        val server = plexPrefs.getServer()
        val library = plexPrefs.getLibrary()
        if (plexAuthToken.isNotEmpty()) {
            plexConfig.authToken = plexAuthToken
        }
        if (server != null) {
            Log.i(APP_NAME, server.connections.toString())
            plexConnectionChooser.addPotentialConnections(server.connections)
            applicationScope.launch {
                val result = plexConnectionChooser.chooseViableConnections(applicationScope)
                if (result is Failure) {
                    Toast.makeText(
                        applicationContext,
                        "Failed to connect to any server",
                        LENGTH_SHORT
                    ).show()
                }
            }
        }
        if (library != null) {
            plexConfig.libraryId = library.id
        }
    }
}
