package io.github.mattpvaughn.chronicle.features.login

import androidx.lifecycle.*
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.data.model.LoadingStatus
import io.github.mattpvaughn.chronicle.data.model.ServerModel
import io.github.mattpvaughn.chronicle.data.model.asServer
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexLibrarySource
import io.github.mattpvaughn.chronicle.navigation.Navigator
import io.github.mattpvaughn.chronicle.util.Event
import io.github.mattpvaughn.chronicle.util.postEvent
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

class ChooseServerViewModel(
    private val plexLibrarySource: PlexLibrarySource,
    private val navigator: Navigator
) : ViewModel() {

    class Factory @Inject constructor(private val navigator: Navigator) :
        ViewModelProvider.Factory {

        lateinit var plexLibrarySource: PlexLibrarySource

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (!this::plexLibrarySource.isInitialized) {
                throw IllegalStateException("No source provided!")
            }
            if (modelClass.isAssignableFrom(ChooseServerViewModel::class.java)) {
                return ChooseServerViewModel(plexLibrarySource, navigator) as T
            }
            throw IllegalArgumentException("Unknown ViewHolder class")
        }
    }

    private val _userMessage = MutableLiveData<Event<String>>()
    val userMessage: LiveData<Event<String>>
        get() = _userMessage

    private var _servers = MutableLiveData(emptyList<ServerModel>())
    val servers: LiveData<List<ServerModel>>
        get() = _servers

    private var _loadingStatus = MutableLiveData(LoadingStatus.LOADING)
    val loadingStatus: LiveData<LoadingStatus>
        get() = _loadingStatus

    init {
        getServers()
    }

    private fun getServers() {
        viewModelScope.launch(Injector.get().unhandledExceptionHandler()) {
            try {
                _loadingStatus.value = LoadingStatus.LOADING
                val serverContainer = plexLibrarySource.fetchServers()
                Timber.i("Server: $serverContainer")
                _loadingStatus.value = LoadingStatus.DONE
                _servers.postValue(serverContainer
                    .filter { it.provides.contains("server") }
                    .map { it.asServer() })
            } catch (e: Throwable) {
                Timber.e(e, "Failed to get servers")
                _userMessage.postEvent("Failed to load servers: ${e.message}")
                _loadingStatus.value = LoadingStatus.ERROR
            }
        }
    }

    fun refresh() {
        getServers()
    }

    fun chooseServer(serverModel: ServerModel) {
        plexLibrarySource.chooseServer(serverModel)
        navigator.showLibraryChooser()
    }
}