package io.github.mattpvaughn.chronicle.features.home

import android.content.SharedPreferences
import androidx.lifecycle.*
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.application.MainActivityViewModel
import io.github.mattpvaughn.chronicle.data.local.IBookRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.model.getProgress
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexConfig
import io.github.mattpvaughn.chronicle.data.sources.plex.model.getDuration
import io.github.mattpvaughn.chronicle.features.library.LibraryViewModel
import io.github.mattpvaughn.chronicle.util.DoubleLiveData
import io.github.mattpvaughn.chronicle.util.Event
import io.github.mattpvaughn.chronicle.util.observeOnce
import io.github.mattpvaughn.chronicle.util.postEvent
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

class HomeViewModel(
    private val plexConfig: PlexConfig,
    private val bookRepository: IBookRepository,
    private val trackRepository: ITrackRepository,
    private val prefsRepo: PrefsRepo
) : ViewModel() {

    @Suppress("UNCHECKED_CAST")
    class Factory @Inject constructor(
        private val plexConfig: PlexConfig,
        private val bookRepository: IBookRepository,
        private val trackRepository: ITrackRepository,
        private val prefsRepo: PrefsRepo,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                return HomeViewModel(
                    plexConfig,
                    bookRepository,
                    trackRepository,
                    prefsRepo
                ) as T
            } else {
                throw IllegalArgumentException("Cannot instantiate $modelClass from HomeViewModel.Factory")
            }
        }
    }

    private var _offlineMode = MutableLiveData(prefsRepo.offlineMode)
    val offlineMode: LiveData<Boolean>
        get() = _offlineMode

    val recentlyListened =
        DoubleLiveData(bookRepository.getRecentlyListened(), _offlineMode) { recents, offline ->
            return@DoubleLiveData if (offline == true) {
                recents?.filter { it.isCached }
            } else {
                Timber.i("Recently listened: $recents")
                recents
            } ?: emptyList()
        }

    private var _isRefreshing = MutableLiveData<Boolean>()
    val isRefreshing: LiveData<Boolean>
        get() = _isRefreshing

    private var _messageForUser = MutableLiveData<Event<String>>()
    val messageForUser: LiveData<Event<String>>
        get() = _messageForUser

    var recentlyAdded: DoubleLiveData<List<Audiobook>, Boolean, List<Audiobook>> =
        DoubleLiveData(bookRepository.getRecentlyAdded(), _offlineMode) { recents, offline ->
            /** We only want books which have actually been listened to! */
            if (offline == true) {
                return@DoubleLiveData recents?.filter { book -> book.isCached } ?: emptyList()
            } else {
                return@DoubleLiveData recents ?: emptyList()
            }
        }

    val downloaded: LiveData<List<Audiobook>> = bookRepository.getCachedAudiobooks()

    private var _isSearchActive = MutableLiveData<Boolean>()
    val isSearchActive: LiveData<Boolean>
        get() = _isSearchActive

    private var _searchResults = MutableLiveData<List<Audiobook>>()
    val searchResults: LiveData<List<Audiobook>>
        get() = _searchResults

    private val offlineModeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                PrefsRepo.KEY_OFFLINE_MODE -> _offlineMode.postValue(prefsRepo.offlineMode)
                else -> { /* Do nothing */
                }
            }
        }

    private val serverConnectionObserver = Observer<Boolean> { isConnectedToServer ->
        if (isConnectedToServer) {
            viewModelScope.launch(Injector.get().unhandledExceptionHandler()) {
                val millisSinceLastRefresh =
                    System.currentTimeMillis() - prefsRepo.lastRefreshTimeStamp
                val minutesSinceLastRefresh = millisSinceLastRefresh / 1000 / 60
                val bookCount = bookRepository.getBookCount()
                val shouldRefresh =
                    minutesSinceLastRefresh > prefsRepo.refreshRateMinutes || bookCount == 0
                Timber.i("$minutesSinceLastRefresh minutes since last libraryrefresh,${prefsRepo.refreshRateMinutes} needed")
                if (shouldRefresh) {
                    refreshData()
                }
            }
        }
    }

    init {
        Timber.i("HomeViewModel init")
        if (plexConfig.isConnected.value == true) {
            // if already connected, call it just once
            serverConnectionObserver.onChanged(true)
        }
        plexConfig.isConnected.observeForever(serverConnectionObserver)
        prefsRepo.registerPrefsListener(offlineModeListener)
    }

    override fun onCleared() {
        plexConfig.isConnected.removeObserver(serverConnectionObserver)
        prefsRepo.unregisterPrefsListener(offlineModeListener)
        super.onCleared()
    }

    fun setSearchActive(isSearchActive: Boolean) {
        _isSearchActive.postValue(isSearchActive)
    }

    fun disableOfflineMode() {
        prefsRepo.offlineMode = false
    }

    /** Searches for books which match the provided text */
    fun search(query: String) {
        if (query.isEmpty()) {
            _searchResults.postValue(emptyList())
        } else {
            bookRepository.search(query).observeOnce(Observer {
                _searchResults.postValue(it)
            })
        }

    }


    /**
     * Pull most recent data from server and update repositories.
     *
     * Update book info for fields where child tracks serve as source of truth, like how
     * [Audiobook.duration] serves as a delegate for [List<MediaItemTrack>.getDuration()]
     *
     * TODO: migrate to [MainActivityViewModel] so code isn't duplicated b/w [HomeViewModel] and
     * [LibraryViewModel]
     */
    fun refreshData() {
        viewModelScope.launch(Injector.get().unhandledExceptionHandler()) {
            try {
                _isRefreshing.postValue(true)
                bookRepository.refreshDataPaginated()
                trackRepository.refreshDataPaginated()
            } catch (e: Throwable) {
                _messageForUser.postEvent("Failed to refresh data: ${e.message}")
            } finally {
                _isRefreshing.postValue(false)
            }

            // Update audiobooks which depend on track data
            val audiobooks = bookRepository.getAllBooksAsync()
            val tracks = trackRepository.getAllTracksAsync()
            audiobooks.forEach { book ->
                // TODO: O(n^2) so could be bad for big libs, grouping by tracks first would be O(n)

                // Not necessarily in the right order, but it doesn't matter for updateTrackData
                val tracksInAudiobook = tracks.filter { it.parentKey == book.id }
                bookRepository.updateTrackData(
                    bookId = book.id,
                    bookProgress = tracksInAudiobook.getProgress(),
                    bookDuration = tracksInAudiobook.getDuration(),
                    trackCount = tracksInAudiobook.size
                )
            }
        }
    }
}
