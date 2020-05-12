package io.github.mattpvaughn.chronicle.features.login

import android.util.Log
import androidx.lifecycle.*
import io.github.mattpvaughn.chronicle.data.model.Library
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME
import io.github.mattpvaughn.chronicle.data.plex.PlexConfig
import io.github.mattpvaughn.chronicle.data.plex.PlexConnectionChooser
import io.github.mattpvaughn.chronicle.data.plex.PlexConnectionChooser.ConnectionResult.Failure
import io.github.mattpvaughn.chronicle.data.plex.PlexMediaService
import io.github.mattpvaughn.chronicle.data.plex.model.MediaType.Companion.ARTIST
import io.github.mattpvaughn.chronicle.data.plex.model.asLibrary
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import javax.inject.Inject


@UseExperimental(InternalCoroutinesApi::class)
class ChooseLibraryViewModel @Inject constructor(
    private val plexMediaService: PlexMediaService,
    plexConnectionChooser: PlexConnectionChooser,
    private val plexConfig: PlexConfig
) : ViewModel() {

    class Factory @Inject constructor(
        private val plexMediaService: PlexMediaService,
        private val plexConnectionChooser: PlexConnectionChooser,
        private val plexConfig: PlexConfig
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ChooseLibraryViewModel::class.java)) {
                return ChooseLibraryViewModel(
                    plexMediaService,
                    plexConnectionChooser,
                    plexConfig
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewHolder class")
        }
    }

    private val _userMessage = MutableLiveData<String>()
    val userMessage: LiveData<String>
        get() = _userMessage

    private var _libraries = MutableLiveData<List<Library>>(emptyList())
    val libraries: LiveData<List<Library>>
        get() = _libraries

    enum class LoadingStatus { LOADING, DONE, ERROR }

    private var _loadingStatus = MutableLiveData(LoadingStatus.LOADING)
    val loadingStatus: LiveData<LoadingStatus>
        get() = _loadingStatus

    private val networkObserver = Observer<Boolean> { isConnected ->
        if (isConnected) {
            Log.i(
                APP_NAME,
                "Network connected: server url is ${plexConfig.url}. Fetching libraries"
            )
            getLibraries()
        }
    }

    init {
        viewModelScope.launch {
            // chooseViableConnections must be called here because it won't be called in
            // ChronicleApplication if we are just coming from login
            val result = plexConnectionChooser.chooseViableConnections(viewModelScope)
            if (result is Failure) {
                _userMessage.postValue("Failed to connect to any server")
            }
        }
        plexConfig.isConnected.observeForever(networkObserver)
    }

    override fun onCleared() {
        plexConfig.isConnected.removeObserver(networkObserver)
        super.onCleared()
    }

    private fun getLibraries() {
        viewModelScope.launch {
            try {
                _loadingStatus.value = LoadingStatus.LOADING
                Log.i(APP_NAME, "Fetching libraries")
                val libraryContainer = plexMediaService.retrieveLibraries()
                val libraries = libraryContainer.mediaContainer.directories
                    .filter { it.type == ARTIST.typeString }
                    .map { it.asLibrary() }
                Log.i(APP_NAME, "Libraries: $libraries")
                _libraries.postValue(libraries)
                _loadingStatus.value = LoadingStatus.DONE
            } catch (e: Exception) {
                Log.e(APP_NAME, "Error: unable to load libraries: $e")
                _loadingStatus.value = LoadingStatus.ERROR
                _libraries.value = emptyList()
            }
        }
    }

    fun refresh() {
        plexConfig.isConnected.observeForever(onHasConnectionObserver)
    }

    private val onHasConnectionObserver: Observer<Boolean> = Observer { isConnectionActive ->
        if (isConnectionActive) {
            removeRefreshObserver()
        }
    }

    private fun removeRefreshObserver() {
        plexConfig.isConnected.removeObserver(onHasConnectionObserver)
    }
}