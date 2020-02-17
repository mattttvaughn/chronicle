package io.github.mattpvaughn.chronicle.features.chooseserver

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.github.mattpvaughn.chronicle.data.plex.*
import kotlinx.coroutines.*

class ChooseServerViewModel(plexPrefsRepo: PlexPrefsRepo) : ViewModel() {

    private var _servers = MutableLiveData<List<ServerModel>>()
    val servers: LiveData<List<ServerModel>>
        get() = _servers

    private var viewModelJob = Job()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + viewModelJob)

    enum class LoadingStatus { LOADING, DONE, ERROR }

    private var _loadingStatus = MutableLiveData<LoadingStatus>(LoadingStatus.LOADING)
    val loadingStatus: LiveData<LoadingStatus>
        get() = _loadingStatus

    private var _chooseLibrary = MutableLiveData<Boolean>(false)
    val chooseLibrary: LiveData<Boolean>
        get() = _chooseLibrary

    init {
        if (PlexRequestSingleton.authToken.isEmpty()) {
            val authToken = plexPrefsRepo.getAuthToken()
            if (authToken.isNotEmpty()) {
                PlexRequestSingleton.authToken = authToken
            }
        }
        getServers()
    }

    private fun getServers() {
        coroutineScope.launch {
            try {
                _loadingStatus.value = LoadingStatus.LOADING
                val serverContainer = PlexLoginApi.retrofitService.resources()
                _loadingStatus.value = LoadingStatus.DONE
                _servers.postValue(serverContainer.asServers())
            } catch (e: Exception) {
                Log.e(APP_NAME, "Failed to get servers: $e")
                e.printStackTrace()
                _loadingStatus.value = LoadingStatus.ERROR
            }
        }
    }

    override fun onCleared() {
        viewModelJob.cancel()
        super.onCleared()
    }

    fun refresh() {
        getServers()
    }
}