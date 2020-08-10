package io.github.mattpvaughn.chronicle.features.login

import androidx.lifecycle.*
import androidx.lifecycle.Observer
import io.github.mattpvaughn.chronicle.data.model.LoadingStatus
import io.github.mattpvaughn.chronicle.data.model.PlexLibrary
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexLibrarySource
import io.github.mattpvaughn.chronicle.data.sources.plex.model.MediaType.Companion.ARTIST
import io.github.mattpvaughn.chronicle.data.sources.plex.model.asLibrary
import io.github.mattpvaughn.chronicle.util.Event
import io.github.mattpvaughn.chronicle.util.postEvent
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*
import javax.inject.Inject


@OptIn(InternalCoroutinesApi::class)
class ChooseLibraryViewModel(private val source: PlexLibrarySource) : ViewModel() {

    class Factory @Inject constructor() : ViewModelProvider.Factory {
        lateinit var source: PlexLibrarySource

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (this::source.isInitialized) {
                throw IllegalStateException("No source provided!")
            }
            if (modelClass.isAssignableFrom(ChooseLibraryViewModel::class.java)) {
                return ChooseLibraryViewModel(source) as T
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

    private var _loadingStatus = MutableLiveData(LoadingStatus.LOADING)
    val loadingStatus: LiveData<LoadingStatus>
        get() = _loadingStatus

    private val networkObserver = Observer<Boolean> { isConnected ->
        if (isConnected) {
            Timber.i("Connected to server at ${source.url}")
            getLibraries()
        }
    }

    init {
        viewModelScope.launch {
            // chooseViableConnections must be called here because it won't be called in
            // ChronicleApplication if we have just logged in
            try {
                source.connectToRemote()
            } catch (t: Throwable) {
                Timber.i("Failed to return result!")
            }
        }
        source.isConnected.observeForever(networkObserver)
    }

    override fun onCleared() {
        source.isConnected.removeObserver(networkObserver)
        super.onCleared()
    }

    private fun getLibraries() {
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
        source.isConnected.removeObserver(networkObserver)
        source.isConnected.observeForever(networkObserver)
    }
}