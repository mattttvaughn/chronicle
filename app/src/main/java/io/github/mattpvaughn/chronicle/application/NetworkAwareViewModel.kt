package io.github.mattpvaughn.chronicle.application

import android.util.Log
import androidx.lifecycle.*
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME
import io.github.mattpvaughn.chronicle.data.plex.PlexPrefsRepo
import io.github.mattpvaughn.chronicle.data.plex.PlexRequestSingleton
import kotlinx.coroutines.*

abstract class NetworkAwareViewModel(plexPrefsRepo: PlexPrefsRepo) : ViewModel() {

    private val validUrlObserver = Observer<String> {
        if (PlexRequestSingleton.isUrlSet()) {
            _isLoading.postValue(false)
            Log.i(APP_NAME, "Network connected: ${PlexRequestSingleton.url}")
            val conn = PlexRequestSingleton.connectionSet.find { it.uri == PlexRequestSingleton.url }
            if (conn != null) {
                _isConnected.postValue(true)
                plexPrefsRepo.putSuccessfulConnection(conn)
            } else {
                _isConnected.postValue(false)
            }
        } else {
           _isConnected.postValue(false)
        }
    }

    private var _isLoading = MutableLiveData<Boolean>(true)
    val isLoading: LiveData<Boolean>
        get() = _isLoading

    private var _isConnected = MutableLiveData<Boolean>(true)
    val isConnected: LiveData<Boolean>
        get() = _isConnected

    init {
        if (PlexRequestSingleton.authToken.isEmpty()) {
            PlexRequestSingleton.authToken = plexPrefsRepo.getAuthToken()
        }
        if (PlexRequestSingleton.libraryId.isEmpty()) {
            PlexRequestSingleton.libraryId = plexPrefsRepo.getLibrary()?.id ?: ""
        }
        if (PlexRequestSingleton.connectionSet.isEmpty()) {
            val connections = plexPrefsRepo.getServer()?.connections
            if (connections != null) {
                PlexRequestSingleton.connectionSet.addAll(connections)
            }
        }
        PlexRequestSingleton.observableUrl.observeForever(validUrlObserver)
        if (!PlexRequestSingleton.isUrlSet()) {
            chooseConnections()
        }
    }


    @UseExperimental(InternalCoroutinesApi::class)
    private fun chooseConnections() {
        viewModelScope.launch {
            PlexRequestSingleton.chooseViableConnections(viewModelScope)
        }
    }

    override fun onCleared() {
        PlexRequestSingleton.observableUrl.removeObserver(validUrlObserver)
        super.onCleared()
    }
}