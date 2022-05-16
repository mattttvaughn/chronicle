package io.github.mattpvaughn.chronicle.features.collections

import android.content.SharedPreferences
import androidx.lifecycle.*
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.data.local.BookRepository
import io.github.mattpvaughn.chronicle.data.local.CollectionsRepository
import io.github.mattpvaughn.chronicle.data.local.LibrarySyncRepository
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.KEY_BOOK_SORT_BY
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.KEY_HIDE_PLAYED_AUDIOBOOKS
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.KEY_IS_LIBRARY_SORT_DESCENDING
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.KEY_LIBRARY_VIEW_STYLE
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.model.Audiobook.Companion.SORT_KEY_TITLE
import io.github.mattpvaughn.chronicle.data.model.Collection
import io.github.mattpvaughn.chronicle.util.*
import io.github.mattpvaughn.chronicle.views.BottomSheetChooser
import io.github.mattpvaughn.chronicle.views.BottomSheetChooser.BottomChooserState.Companion.EMPTY_BOTTOM_CHOOSER
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

class CollectionsViewModel(
    private val prefsRepo: PrefsRepo,
    private val librarySyncRepository: LibrarySyncRepository,
    collectionsRepository: CollectionsRepository,
    sharedPreferences: SharedPreferences,
    private val bookRepository: BookRepository
) : ViewModel() {

    @Suppress("UNCHECKED_CAST")
    class Factory @Inject constructor(
        private val prefsRepo: PrefsRepo,
        private val collectionsRepository: CollectionsRepository,
        private val librarySyncRepository: LibrarySyncRepository,
        private val sharedPreferences: SharedPreferences,
        private val bookRepository: BookRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CollectionsViewModel::class.java)) {
                return CollectionsViewModel(
                    prefsRepo,
                    librarySyncRepository,
                    collectionsRepository,
                    sharedPreferences,
                    bookRepository
                ) as T
            } else {
                throw IllegalArgumentException("Cannot instantiate $modelClass from LibraryViewModel.Factory")
            }
        }
    }

    val isRefreshing = librarySyncRepository.isRefreshing

    private var _isSearchActive = MutableLiveData<Boolean>()
    val isSearchActive: LiveData<Boolean>
        get() = _isSearchActive

    val viewStyle = StringPreferenceLiveData(
        KEY_LIBRARY_VIEW_STYLE,
        prefsRepo.libraryBookViewStyle,
        sharedPreferences
    )

    val isSortDescending = BooleanPreferenceLiveData(
        KEY_IS_LIBRARY_SORT_DESCENDING,
        true,
        sharedPreferences
    )

    val arePlayedAudiobooksHidden = BooleanPreferenceLiveData(
        KEY_HIDE_PLAYED_AUDIOBOOKS,
        false,
        sharedPreferences
    )

    private val sortKey = StringPreferenceLiveData(
        KEY_BOOK_SORT_BY,
        SORT_KEY_TITLE,
        sharedPreferences
    )

    private var prevCollections = emptyList<Collection>()

    private val allCollections = collectionsRepository.getAllCollections()
    val collections = QuadLiveDataAsync(
        viewModelScope,
        allCollections,
        isSortDescending,
        sortKey,
        arePlayedAudiobooksHidden,
    ) { _collections, _isDescending, _sortKey, _hidePlayed ->
        if (_collections.isNullOrEmpty()) {
            return@QuadLiveDataAsync emptyList<Collection>()
        }

        // TODO: Currently only support sort by title!
        val key = SORT_KEY_TITLE

        // Use defaults if provided null values
        val desc = _isDescending ?: true
        val hidePlayed = _hidePlayed ?: false

        val results = _collections.sortedWith(Comparator { coll1, coll2 ->
                val descMultiplier = if (desc) 1 else -1
                return@Comparator descMultiplier * when (key) {
                    SORT_KEY_TITLE -> coll1.title.compareTo(coll2.title)
                    else -> throw NoWhenBranchMatchedException("Unknown sort key: $key")
                }
            })

        // If nothing has changed, return prevBooks
        if (prevCollections.map { it.id } == results.map { it.id }) {
            return@QuadLiveDataAsync prevCollections
        }

        prevCollections = results

        return@QuadLiveDataAsync results
    }

    private var _messageForUser = MutableLiveData<Event<String>>()
    val messageForUser: LiveData<Event<String>>
        get() = _messageForUser

    private var _searchResults = MutableLiveData<List<Audiobook>>()
    val searchResults: LiveData<List<Audiobook>>
        get() = _searchResults

    private var _isQueryEmpty = MutableLiveData<Boolean>(false)
    val isQueryEmpty: LiveData<Boolean>
        get() = _isQueryEmpty

    private var _bottomChooserState = MutableLiveData(EMPTY_BOTTOM_CHOOSER)
    val bottomChooserState: LiveData<BottomSheetChooser.BottomChooserState>
        get() = _bottomChooserState

    fun setSearchActive(isSearchActive: Boolean) {
        _isSearchActive.postValue(isSearchActive)
    }

    /** Searches for books which match the provided text */
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

    private val serverConnectionObserver = Observer<Boolean> { isConnectedToServer ->
        if (isConnectedToServer) {
            viewModelScope.launch(Injector.get().unhandledExceptionHandler()) {
                val millisSinceLastRefresh =
                    System.currentTimeMillis() - prefsRepo.lastRefreshTimeStamp
                val minutesSinceLastRefresh = millisSinceLastRefresh / 1000 / 60
                val bookCount = bookRepository.getBookCount()
                val shouldRefresh =
                    minutesSinceLastRefresh > prefsRepo.refreshRateMinutes || bookCount == 0
                Timber.i(
                    """$minutesSinceLastRefresh minutes since last libraryrefresh,
                    |${prefsRepo.refreshRateMinutes} needed""".trimMargin()
                )
                if (shouldRefresh) {
                    refreshData()
                }
            }
        }
    }

    fun disableOfflineMode() {
        prefsRepo.offlineMode = false
    }

    fun refreshData() {
        librarySyncRepository.refreshLibrary()
    }

    /** Toggles whether to show or hide played audiobooks in the library */
    fun toggleHidePlayedAudiobooks() {
        Timber.i("toggleHidePlayedAudiobooks")
        prefsRepo.hidePlayedAudiobooks = !prefsRepo.hidePlayedAudiobooks
    }

}
