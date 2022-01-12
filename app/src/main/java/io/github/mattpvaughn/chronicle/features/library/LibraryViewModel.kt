package io.github.mattpvaughn.chronicle.features.library

import android.content.SharedPreferences
import android.text.format.Formatter
import androidx.lifecycle.*
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.data.local.IBookRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.KEY_BOOK_SORT_BY
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.KEY_IS_LIBRARY_SORT_DESCENDING
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.KEY_LIBRARY_VIEW_STYLE
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.KEY_OFFLINE_MODE
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.model.Audiobook.Companion.SORT_KEY_AUTHOR
import io.github.mattpvaughn.chronicle.data.model.Audiobook.Companion.SORT_KEY_DATE_ADDED
import io.github.mattpvaughn.chronicle.data.model.Audiobook.Companion.SORT_KEY_DATE_PLAYED
import io.github.mattpvaughn.chronicle.data.model.Audiobook.Companion.SORT_KEY_DURATION
import io.github.mattpvaughn.chronicle.data.model.Audiobook.Companion.SORT_KEY_PLAYS
import io.github.mattpvaughn.chronicle.data.model.Audiobook.Companion.SORT_KEY_TITLE
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import io.github.mattpvaughn.chronicle.data.model.getProgress
import io.github.mattpvaughn.chronicle.data.sources.plex.ICachedFileManager
import io.github.mattpvaughn.chronicle.data.sources.plex.ICachedFileManager.CacheStatus.CACHED
import io.github.mattpvaughn.chronicle.data.sources.plex.ICachedFileManager.CacheStatus.NOT_CACHED
import io.github.mattpvaughn.chronicle.data.sources.plex.model.getDuration
import io.github.mattpvaughn.chronicle.util.*
import io.github.mattpvaughn.chronicle.views.BottomSheetChooser
import io.github.mattpvaughn.chronicle.views.BottomSheetChooser.BottomChooserListener
import io.github.mattpvaughn.chronicle.views.BottomSheetChooser.BottomChooserState.Companion.EMPTY_BOTTOM_CHOOSER
import io.github.mattpvaughn.chronicle.views.BottomSheetChooser.FormattableString
import io.github.mattpvaughn.chronicle.views.BottomSheetChooser.FormattableString.ResourceString
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

class LibraryViewModel(
    private val bookRepository: IBookRepository,
    private val trackRepository: ITrackRepository,
    private val prefsRepo: PrefsRepo,
    private val cachedFileManager: ICachedFileManager,
    sharedPreferences: SharedPreferences
) : ViewModel() {

    @Suppress("UNCHECKED_CAST")
    class Factory @Inject constructor(
        private val bookRepository: IBookRepository,
        private val trackRepository: ITrackRepository,
        private val prefsRepo: PrefsRepo,
        private val cachedFileManager: ICachedFileManager,
        private val sharedPreferences: SharedPreferences
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
                return LibraryViewModel(
                    bookRepository,
                    trackRepository,
                    prefsRepo,
                    cachedFileManager,
                    sharedPreferences
                ) as T
            } else {
                throw IllegalArgumentException("Cannot instantiate $modelClass from LibraryViewModel.Factory")
            }
        }
    }

    private var _isRefreshing = MutableLiveData<Boolean>()
    val isRefreshing: LiveData<Boolean>
        get() = _isRefreshing

    private var _isSearchActive = MutableLiveData<Boolean>()
    val isSearchActive: LiveData<Boolean>
        get() = _isSearchActive

    val viewStyle = StringPreferenceLiveData(
        KEY_LIBRARY_VIEW_STYLE,
        prefsRepo.libraryBookViewStyle,
        sharedPreferences
    )

    private var _isFilterShown = MutableLiveData(false)
    val isFilterShown: LiveData<Boolean>
        get() = _isFilterShown

    val isSortDescending = BooleanPreferenceLiveData(
        KEY_IS_LIBRARY_SORT_DESCENDING,
        true,
        sharedPreferences
    )

    private val sortKey =
        StringPreferenceLiveData(KEY_BOOK_SORT_BY, SORT_KEY_TITLE, sharedPreferences)
    val isOffline = BooleanPreferenceLiveData(KEY_OFFLINE_MODE, false, sharedPreferences)

    private var prevBooks = emptyList<Audiobook>()

    private val allBooks = bookRepository.getAllBooks()
    val books = QuadLiveDataAsync(
        viewModelScope,
        allBooks,
        isSortDescending,
        sortKey,
        isOffline
    ) { _books, _isDescending, _sortKey, _isOffline ->
        if (_books.isNullOrEmpty()) {
            return@QuadLiveDataAsync emptyList<Audiobook>()
        }

        // Use defaults if provided null values
        val desc = _isDescending ?: true
        val key = _sortKey ?: SORT_KEY_TITLE
        val offline = _isOffline ?: false

        val results = _books.filter { !offline || it.isCached && offline }
            .sortedWith(Comparator { book1, book2 ->
                val descMultiplier = if (desc) 1 else -1
                return@Comparator descMultiplier * when (key) {
                    SORT_KEY_AUTHOR -> book1.author.compareTo(book2.author)
                    SORT_KEY_TITLE -> sortTitleComparator(book1.titleSort, book2.titleSort)
                    SORT_KEY_PLAYS -> book2.viewedLeafCount.compareTo(book1.viewedLeafCount)
                    SORT_KEY_DURATION -> book2.duration.compareTo(book1.duration)
                    // Note: Reverse order for timestamps, because most recent should be at the top
                    // of descending, even though the timestamp is larger
                    SORT_KEY_DATE_ADDED -> book2.addedAt.compareTo(book1.addedAt)
                    SORT_KEY_DATE_PLAYED -> book2.lastViewedAt.compareTo(book1.lastViewedAt)
                    else -> throw NoWhenBranchMatchedException("Unknown sort key: $key")
                }
            })

        // If nothing has changed, return prevBooks
        if (prevBooks.map { it.id } == results.map { it.id }) {
            return@QuadLiveDataAsync prevBooks
        }

        prevBooks = results

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

    private var _tracks = trackRepository.getAllTracks()
    val tracks: LiveData<List<MediaItemTrack>>
        get() = _tracks

    private val cacheStatus = Transformations.map(tracks) {
        when {
            it.isEmpty() -> NOT_CACHED
            it.all { track -> track.cached } -> CACHED
            it.any { track -> !track.cached } -> NOT_CACHED
            else -> NOT_CACHED
        }
    }

    /** Compares Book titles using numerical ordering */
    private fun sortTitleComparator(bookTitle1: String, bookTitle2: String): Int {
        fun titleToArray(bookTitle: String): List<String> {
            return bookTitle.split(Regex("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)"))
        }

        val title1: Iterator<String> = titleToArray(bookTitle1).iterator()
        val title2: Iterator<String> = titleToArray(bookTitle2).iterator()

        while (true) {
            val bool1: Boolean = title1.hasNext()
            val bool2: Boolean = title2.hasNext()
            if (!(bool1 || bool2)) {
                return 0
            }
            if (!bool1) return -1
            if (!bool2) return 1

            val next1: String = title1.next()
            val next2: String = title2.next()

            try {
                if (next1.toInt() > next2.toInt())
                    return 1
                else if (next1.toInt() < next2.toInt())
                    return -1
            } catch (e: NumberFormatException) {
                val comp: Int = next1.compareTo(next2)
                if (comp != 0) return comp
            }
        }
    }

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

    /**
     * Prompt the user with a choice to download all audiobooks on device, presenting the user
     * with information on the space available in the sync directory and the space required
     */
    fun promptDownloadAll() {
        // Calculate space available
        val bytesAvailable = prefsRepo.cachedMediaDir.bytesAvailable()

        // Calculate space required
        viewModelScope.launch(Injector.get().unhandledExceptionHandler()) {
            val tracks = try {
                trackRepository.loadAllTracksAsync()
            } catch (e: Throwable) {
                Timber.e("Failed to load tracks!")
                emptyList<MediaItemTrack>()
            }
            var bytesToBeUsed = 0L
            tracks.forEach { bytesToBeUsed += it.size }
            val downloadSize =
                Formatter.formatFileSize(Injector.get().applicationContext(), bytesToBeUsed)
            val availableStorage =
                Formatter.formatFileSize(Injector.get().applicationContext(), bytesAvailable)
            val prompt = ResourceString(
                stringRes = R.string.download_all_prompt,
                placeHolderStrings = listOf(downloadSize, availableStorage)
            )

            showOptionsMenu(
                prompt,
                listOf(FormattableString.yes, FormattableString.no),
                object : BottomSheetChooser.BottomChooserItemListener() {
                    override fun onItemClicked(formattableString: FormattableString) {
                        when (formattableString) {
                            FormattableString.yes -> downloadAll(tracks)
                            FormattableString.no -> {
                            }
                            else -> throw NoWhenBranchMatchedException()
                        }
                    }
                })
        }

        // Prompt user to download
    }

    private fun downloadAll(tracks: List<MediaItemTrack>) {
//        cachedFileManager.downloadTracks(tracks)
    }

    private fun showOptionsMenu(
        title: FormattableString,
        options: List<FormattableString>,
        listener: BottomChooserListener
    ) {
        _bottomChooserState.postValue(
            BottomSheetChooser.BottomChooserState(
                title = title,
                options = options,
                listener = listener,
                shouldShow = true
            )
        )
    }

    /**
     * Pull most recent data from server and update repositories.
     *
     * Update book info for fields where child tracks serve as source of truth, like how
     * [Audiobook.duration] serves as a delegate for [List<MediaItemTrack>.getDuration()]
     *
     * TODO: maybe refresh data in the repository whenever a query is made? repeating code b/w
     *       here and [HomeViewModel]
     */
    fun refreshData() {
        viewModelScope.launch(Injector.get().unhandledExceptionHandler()) {
            try {
                _isRefreshing.postValue(true)
                bookRepository.refreshData()
                trackRepository.refreshData()
            } catch (e: Throwable) {
                _messageForUser.postEvent("Failed to refresh data")
            } finally {
                _isRefreshing.postValue(false)
            }

            val audiobooks = bookRepository.getAllBooksAsync()
            val tracks = trackRepository.getAllTracksAsync()
            audiobooks.forEach { book ->
                val tracksInAudiobook = tracks.filter { it.parentKey == book.id }
                Timber.i("Book progress: ${tracksInAudiobook.getProgress()}")
                bookRepository.updateTrackData(
                    bookId = book.id,
                    bookProgress = tracksInAudiobook.getProgress(),
                    bookDuration = tracksInAudiobook.getDuration(),
                    trackCount = tracksInAudiobook.size
                )
            }
        }
    }

    /** Shows/hides the filter/sort/view menu to the user. Show if [isVisible] is true, hide otherwise */
    fun setFilterMenuVisible(isVisible: Boolean) {
        if (isVisible != _isFilterShown.value) {
            _isFilterShown.postValue(isVisible)
        }
    }

    /** Toggles the direction which the library is sorted in (ascending vs. descending) */
    fun toggleSortDirection() {
        prefsRepo.isLibrarySortedDescending = !prefsRepo.isLibrarySortedDescending
    }

}
