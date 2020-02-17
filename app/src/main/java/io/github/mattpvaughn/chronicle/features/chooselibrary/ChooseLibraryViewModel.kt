package io.github.mattpvaughn.chronicle.features.chooselibrary

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import io.github.mattpvaughn.chronicle.application.NetworkAwareViewModel
import io.github.mattpvaughn.chronicle.data.plex.PlexMediaApi
import io.github.mattpvaughn.chronicle.data.plex.PlexPrefsRepo
import io.github.mattpvaughn.chronicle.data.plex.PlexRequestSingleton
import kotlinx.coroutines.launch

class ChooseLibraryViewModel(val prefs: PlexPrefsRepo) : NetworkAwareViewModel(prefs) {

    private var _libraries = MutableLiveData<List<LibraryModel>>()
    val libraries: LiveData<List<LibraryModel>>
        get() = _libraries

    enum class LoadingStatus { LOADING, DONE, ERROR }

    private var _loadingStatus = MutableLiveData<LoadingStatus>(LoadingStatus.LOADING)
    val loadingStatus: LiveData<LoadingStatus>
        get() = _loadingStatus

    private val networkObserver = Observer<Boolean> { isLoading ->
        if (!isLoading) {
            getLibraries()
        }
    }

    init {
        isLoading.observeForever(networkObserver)
        if (PlexRequestSingleton.authToken.isEmpty()) {
            PlexRequestSingleton.authToken = prefs.getAuthToken()
        }
        if (PlexRequestSingleton.connectionSet.isEmpty()) {
            val server = prefs.getServer()
                ?: throw IllegalStateException("Server cannot be null when choosing library")
            PlexRequestSingleton.connectionSet.addAll(server.connections)
        }
    }

    override fun onCleared() {
        isLoading.removeObserver(networkObserver)
        super.onCleared()
    }

    private fun getLibraries() {
        viewModelScope.launch {
            // Not using a repository for this because caching is unnecessary when we only expect'
            // to call the function once
            try {
                _loadingStatus.value = LoadingStatus.LOADING
                val getLibraries = PlexMediaApi.retrofitService.retrieveSections()
                _libraries.postValue(getLibraries.asAudioLibraries())
                _loadingStatus.value = LoadingStatus.DONE
            } catch (e: Exception) {
                _loadingStatus.value = LoadingStatus.ERROR
                _libraries.value = ArrayList()
            }
        }
    }

    fun refresh() {
        isLoading.observeForever(onHasConnectionObserver)
    }

    private val onHasConnectionObserver: Observer<Boolean> = Observer { isConnectionActive ->
        if (isConnectionActive) {
            removeRefreshObserver()
        }
    }

    private fun removeRefreshObserver() {
        isLoading.removeObserver(onHasConnectionObserver)
    }
}