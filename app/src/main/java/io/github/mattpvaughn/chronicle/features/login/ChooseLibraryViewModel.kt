package io.github.mattpvaughn.chronicle.features.login

import androidx.lifecycle.*
import androidx.lifecycle.Observer
import io.github.mattpvaughn.chronicle.data.model.LoadingStatus
import io.github.mattpvaughn.chronicle.data.model.PlexLibrary
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexConfig
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexMediaService
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexPrefsRepo
import io.github.mattpvaughn.chronicle.data.sources.plex.model.MediaType.Companion.ARTIST
import io.github.mattpvaughn.chronicle.data.sources.plex.model.asLibrary
import io.github.mattpvaughn.chronicle.util.DoubleLiveData
import io.github.mattpvaughn.chronicle.util.Event
import io.github.mattpvaughn.chronicle.util.postEvent
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*
import javax.inject.Inject


@OptIn(InternalCoroutinesApi::class)
class ChooseLibraryViewModel @Inject constructor(
    private val plexMediaService: PlexMediaService,
    private val plexConfig: PlexConfig,
    private val plexPrefsRepo: PlexPrefsRepo
) : ViewModel() {

    class Factory @Inject constructor(
        private val plexMediaService: PlexMediaService,
        private val plexConfig: PlexConfig,
        private val plexPrefsRepo: PlexPrefsRepo
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ChooseLibraryViewModel::class.java)) {
                return ChooseLibraryViewModel(
                    plexMediaService,
                    plexConfig,
                    plexPrefsRepo
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewHolder class")
        }
    }

    private val _userMessage = MutableLiveData<Event<String>>()
    val userMessage: LiveData<Event<String>>
        get() = _userMessage

    private var _libraries = MutableLiveData<List<PlexLibrary>>(emptyList())
    val libraries: LiveData<List<PlexLibrary>>
        get() = _libraries

    /**
     * LoadingStatus represents the status of the "connected to server" state as well as the
     * "fetched libraries" state
     */
    private var _loadingStatus = MutableLiveData(LoadingStatus.LOADING)
    val loadingStatus: LiveData<LoadingStatus> =
        DoubleLiveData(plexConfig.connectionState, _loadingStatus) { serverConn, loadingConn ->
            when (serverConn) {
                PlexConfig.ConnectionState.CONNECTING -> LoadingStatus.LOADING
                PlexConfig.ConnectionState.NOT_CONNECTED -> LoadingStatus.LOADING
                PlexConfig.ConnectionState.CONNECTED -> loadingConn
                PlexConfig.ConnectionState.CONNECTION_FAILED -> LoadingStatus.ERROR
                null -> throw IllegalStateException("Cannot have a null server connection!")
            }
        }

    private val networkObserver = Observer<Boolean> { isConnected ->
        if (isConnected) {
            Timber.i("Connected to server at ${plexConfig.url}, fetching libraries with token ${plexPrefsRepo.server?.accessToken}")
            loadLibraries()
        }
    }


    init {
        viewModelScope.launch {
            // chooseViableConnections must be called here because it won't be called in
            // ChronicleApplication if we have just logged in
            try {
                plexConfig.connectToServer(plexMediaService)
            } catch (t: Throwable) {
                Timber.i("Failed to return result!")
            }
        }
        plexConfig.isConnected.observeForever(networkObserver)
    }

    override fun onCleared() {
        plexConfig.isConnected.removeObserver(networkObserver)
        super.onCleared()
    }

    private fun loadLibraries() {
        viewModelScope.launch {
            try {
                _loadingStatus.value = LoadingStatus.LOADING
                val libraryContainer = plexMediaService.retrieveLibraries()
                val tempLibraries = libraryContainer.plexMediaContainer.plexDirectories
                    .filter { it.type == ARTIST.typeString }
                    .map { it.asLibrary() }
                Timber.i("Libraries: $tempLibraries")
                _libraries.postValue(tempLibraries)
                _loadingStatus.value = LoadingStatus.DONE
            } catch (e: Throwable) {
                Timber.e("Error loading libraries: ${Arrays.toString(e.stackTrace)}")
                _userMessage.postEvent("Unable to load libraries: ${e.message}")
                _loadingStatus.value = LoadingStatus.ERROR
            }
        }
    }

    fun refresh() {
        plexConfig.isConnected.removeObserver(networkObserver)
        plexConfig.isConnected.observeForever(networkObserver)
    }
}