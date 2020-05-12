package io.github.mattpvaughn.chronicle.features.login

import android.util.Log
import androidx.lifecycle.*
import io.github.mattpvaughn.chronicle.data.model.ServerModel
import io.github.mattpvaughn.chronicle.data.model.asServer
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME
import io.github.mattpvaughn.chronicle.data.plex.PlexConfig
import io.github.mattpvaughn.chronicle.data.plex.PlexLoginService
import io.github.mattpvaughn.chronicle.data.plex.PlexPrefsRepo
import kotlinx.coroutines.launch
import javax.inject.Inject

class ChooseServerViewModel @Inject constructor(
    plexConfig: PlexConfig,
    private val plexLoginService: PlexLoginService,
    plexPrefsRepo: PlexPrefsRepo
) : ViewModel() {

    class Factory @Inject constructor(
        private val plexPrefsRepo: PlexPrefsRepo,
        private val plexLoginService: PlexLoginService,
        private val plexConfig: PlexConfig
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ChooseServerViewModel::class.java)) {
                return ChooseServerViewModel(
                    plexConfig,
                    plexLoginService,
                    plexPrefsRepo
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewHolder class")
        }
    }

    private var _servers = MutableLiveData(emptyList<ServerModel>())
    val servers: LiveData<List<ServerModel>>
        get() = _servers

    enum class LoadingStatus { LOADING, DONE, ERROR }

    private var _loadingStatus = MutableLiveData(LoadingStatus.LOADING)
    val loadingStatus: LiveData<LoadingStatus>
        get() = _loadingStatus

    init {
        if (plexConfig.authToken.isEmpty()) {
            val authToken = plexPrefsRepo.getAuthToken()
            if (authToken.isNotEmpty()) {
                plexConfig.authToken = authToken
            }
        }
        getServers()
    }

    private fun getServers() {
        viewModelScope.launch {
            try {
                _loadingStatus.value =
                    LoadingStatus.LOADING
                val serverContainer = plexLoginService.resources()
                Log.i(APP_NAME, "Server: $serverContainer")
                _loadingStatus.value =
                    LoadingStatus.DONE
                _servers.postValue(serverContainer.filter { it.provides.contains("server") }
                    .map { it.asServer() })
            } catch (e: Exception) {
                Log.e(APP_NAME, "Failed to get servers: $e")
                e.printStackTrace()
                _loadingStatus.value =
                    LoadingStatus.ERROR
            }
        }
    }

    fun refresh() {
        getServers()
    }
}