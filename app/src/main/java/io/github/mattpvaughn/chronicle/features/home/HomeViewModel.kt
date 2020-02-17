package io.github.mattpvaughn.chronicle.features.home

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.*
import io.github.mattpvaughn.chronicle.application.NetworkAwareViewModel
import io.github.mattpvaughn.chronicle.data.local.IBookRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME
import io.github.mattpvaughn.chronicle.data.plex.PlexPrefsRepo
import io.github.mattpvaughn.chronicle.features.settings.PrefsRepo
import io.github.mattpvaughn.chronicle.util.observeOnce
import kotlinx.coroutines.launch

class HomeViewModel(
    private val bookRepository: IBookRepository,
    private val trackRepository: ITrackRepository,
    plexPrefsRepo: PlexPrefsRepo,
    private val prefsRepo: PrefsRepo
) : NetworkAwareViewModel(plexPrefsRepo) {

    private var _recentlyListened = MutableLiveData<List<Audiobook>>(emptyList())
    var recentlyListened = Transformations.map(_recentlyListened) {
        /** We only want books which have actually been listened to! */
        return@map it.filter { book -> book.lastViewedAt != 0L }
    }

    private var _offlineMode = MutableLiveData<Boolean>(prefsRepo.offlineMode)
    val offlineMode: LiveData<Boolean>
        get() = _offlineMode

    private var _recentlyAdded = bookRepository.getRecentlyAdded()
    val recentlyAdded: LiveData<List<Audiobook>>
        get() = _recentlyAdded

    private var _downloaded = bookRepository.getCachedAudiobooks()
    val downloaded: LiveData<List<Audiobook>>
        get() = _downloaded

    private var _isSearchActive = MutableLiveData<Boolean>()
    val isSearchActive: LiveData<Boolean>
        get() = _isSearchActive

    private var _searchResults = MutableLiveData<List<Audiobook>>()
    val searchResults: LiveData<List<Audiobook>>
        get() = _searchResults

    private var _isQueryEmpty = MutableLiveData<Boolean>(false)
    val isQueryEmpty: LiveData<Boolean>
        get() = _isQueryEmpty

    private fun refreshBookData() {
        viewModelScope.launch {
            try {
                bookRepository.refreshData(trackRepository)
            } catch (e: Exception) {
                Log.e(APP_NAME, "Error loading book data!")
            }
        }
    }

    private val networkObserver = Observer<Boolean> { isLoading ->
        if (!isLoading) {
            refreshBookData()
        }
    }

    private val offlineModeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                PrefsRepo.KEY_OFFLINE_MODE -> _offlineMode.postValue(prefsRepo.offlineMode)
                else -> { /* Do nothing */
                }
            }
        }

    init {
        prefsRepo.registerPrefsListener(offlineModeListener)
        isLoading.observeForever(networkObserver)
        viewModelScope.launch {
            _recentlyListened.postValue(bookRepository.getRecentlyListenedAsync())
        }
    }

    override fun onCleared() {
        isLoading.removeObserver(networkObserver)
        prefsRepo.unRegisterPrefsListener(offlineModeListener)
        super.onCleared()
    }

    fun setSearchActive(isSearchActive: Boolean) {
        _isSearchActive.postValue(isSearchActive)
    }

    fun disableOfflineMode() {
        Log.i(APP_NAME, "Offline mode disabled!")
        prefsRepo.offlineMode = false
        _recentlyAdded = bookRepository.getRecentlyAdded()
        _downloaded = bookRepository.getCachedAudiobooks()
        viewModelScope.launch {
            _recentlyListened.postValue(bookRepository.getRecentlyListenedAsync())
        }
    }

    /**
     * Searches for books which match the provided text
     */
    fun search(query: String) {
        _isQueryEmpty.postValue(query.isEmpty())
        if (query.isEmpty()) {
            _searchResults.postValue(emptyList())
        } else {
            bookRepository.search(query).observeOnce(Observer {
                _searchResults.postValue(it)
            })
        }

    }
}