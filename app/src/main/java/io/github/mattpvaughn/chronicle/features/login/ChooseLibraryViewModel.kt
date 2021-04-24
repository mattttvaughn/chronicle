package io.github.mattpvaughn.chronicle.features.login

import androidx.lifecycle.*
import androidx.lifecycle.Observer
import io.github.mattpvaughn.chronicle.data.ConnectionState
import io.github.mattpvaughn.chronicle.data.model.LoadingStatus
import io.github.mattpvaughn.chronicle.data.model.PlexLibrary
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexLibrarySource
import io.github.mattpvaughn.chronicle.data.sources.plex.model.MediaType.Companion.ARTIST
import io.github.mattpvaughn.chronicle.data.sources.plex.model.asLibrary
import io.github.mattpvaughn.chronicle.navigation.Navigator
import io.github.mattpvaughn.chronicle.util.DoubleLiveData
import io.github.mattpvaughn.chronicle.util.Event
import io.github.mattpvaughn.chronicle.util.postEvent
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*
import javax.inject.Inject


@OptIn(InternalCoroutinesApi::class)
class ChooseLibraryViewModel(
    private val source: PlexLibrarySource,
    private val navigator: Navigator
) : ViewModel() {

    class Factory @Inject constructor(private val navigator: Navigator) :
        ViewModelProvider.Factory {
        lateinit var source: PlexLibrarySource

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(this::source.isInitialized) { "No source provided!" }
            if (modelClass.isAssignableFrom(ChooseLibraryViewModel::class.java)) {
                return ChooseLibraryViewModel(source, navigator) as T
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
    val loadingStatus: LiveData<LoadingStatus>
        get() = _loadingStatus

    private val networkObserver = Observer<ConnectionState> { connection ->
        if (connection == ConnectionState.CONNECTED) {
            Timber.i("Connected to server at ${source.url}")
            loadLibraries()
        }
    }


    init {
        viewModelScope.launch {
            // connectToRemote must be called here because it won't be called in ChronicleApplication
            try {
                source.connectToRemote()
            } catch (t: Throwable) {
                Timber.i("Failed to return result!")
            }
        }
        source.connectionState.observeForever(networkObserver)
    }

    override fun onCleared() {
        source.connectionState.removeObserver(networkObserver)
        super.onCleared()
    }

    private fun loadLibraries() {
        viewModelScope.launch {
            try {
                _loadingStatus.value = LoadingStatus.LOADING
                val libraryContainer = source.fetchLibraries()
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
        source.connectionState.removeObserver(networkObserver)
        source.connectionState.observeForever(networkObserver)
    }

    fun chooseLibrary(library: PlexLibrary) {
        source.chooseLibrary(library)
        navigator.showHome()
    }
}