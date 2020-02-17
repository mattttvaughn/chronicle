package io.github.mattpvaughn.chronicle.application

import android.app.Application
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import io.github.mattpvaughn.chronicle.BuildConfig
import io.github.mattpvaughn.chronicle.data.plex.PlexPrefsRepo
import io.github.mattpvaughn.chronicle.data.plex.PlexRequestSingleton
import io.github.mattpvaughn.chronicle.injection.components.AppComponent
import io.github.mattpvaughn.chronicle.injection.components.DaggerAppComponent
import io.github.mattpvaughn.chronicle.injection.modules.AppModule
import kotlinx.coroutines.*
import javax.inject.Singleton


@Singleton
open class ChronicleApplication : Application() {
    // Instance of the AppComponent that will be used by all the Activities in the project
    val appComponent = initializeComponent()

    init {
        INSTANCE = this
    }

    private var applicationJob = Job()
    private val applicationScope = CoroutineScope(applicationJob + Dispatchers.Main)

    override fun onCreate() {
        if (STRICT_MODE && BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork() // or .detectAll() for all detectable problems
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .penaltyDeath()
                    .build()
            )
        }

        setupNetwork(Injector.get().plexPrefs())
        super.onCreate()
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
        val lastSuccessfulConn = plexPrefs.getLastSuccessfulConnection()
        if (plexAuthToken.isNotEmpty()) {
            PlexRequestSingleton.authToken = plexAuthToken
        }
        if (server != null) {
            PlexRequestSingleton.connectionSet.addAll(server.connections)
        }
        if (library != null) {
            PlexRequestSingleton.libraryId = library.id
        }
        if (lastSuccessfulConn != null) {
            PlexRequestSingleton.url = lastSuccessfulConn.uri
        }
        if (!PlexRequestSingleton.isUrlSet()) {
            applicationScope.launch {
                PlexRequestSingleton.chooseViableConnections(applicationScope)
            }
        }
    }

}