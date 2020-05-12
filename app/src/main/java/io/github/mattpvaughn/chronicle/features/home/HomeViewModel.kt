package io.github.mattpvaughn.chronicle.features.home

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.*
import io.github.mattpvaughn.chronicle.data.local.IBookRepository
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME
import io.github.mattpvaughn.chronicle.util.observeOnce
import kotlinx.coroutines.launch
import javax.inject.Inject

class HomeViewModel @Inject constructor(
    private val bookRepository: IBookRepository,
    private val prefsRepo: PrefsRepo
) : ViewModel() {

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
        viewModelScope.launch {
            _recentlyListened.postValue(bookRepository.getRecentlyListenedAsync())
        }
    }

    override fun onCleared() {
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