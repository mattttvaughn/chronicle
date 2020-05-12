package io.github.mattpvaughn.chronicle.features.library

import android.content.SharedPreferences
import android.text.format.Formatter
import android.util.Log
import androidx.lifecycle.*
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.data.local.IBookRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME
import io.github.mattpvaughn.chronicle.data.plex.ICachedFileManager
import io.github.mattpvaughn.chronicle.data.plex.ICachedFileManager.CacheStatus.CACHED
import io.github.mattpvaughn.chronicle.data.plex.ICachedFileManager.CacheStatus.NOT_CACHED
import io.github.mattpvaughn.chronicle.util.bytesAvailable
import io.github.mattpvaughn.chronicle.util.observeOnce
import io.github.mattpvaughn.chronicle.views.BottomSheetChooser.ItemSelectedListener
import kotlinx.coroutines.launch
import javax.inject.Inject

class LibraryViewModel @Inject constructor(
    private val bookRepository: IBookRepository,
    private val trackRepository: ITrackRepository,
    private val prefsRepo: PrefsRepo,
    private val cachedFileManager: ICachedFileManager
) : ViewModel() {

    private var _books = bookRepository.getAllBooks()
    val books: LiveData<List<Audiobook>>
        get() = _books

    private var _isNetworkError = MutableLiveData<Boolean>()
    val isNetworkError: LiveData<Boolean>
        get() = _isNetworkError

    private var _isSearchActive = MutableLiveData<Boolean>()
    val isSearchActive: LiveData<Boolean>
        get() = _isSearchActive

    private var _searchResults = MutableLiveData<List<Audiobook>>()
    val searchResults: LiveData<List<Audiobook>>
        get() = _searchResults

    private var _isQueryEmpty = MutableLiveData<Boolean>(false)
    val isQueryEmpty: LiveData<Boolean>
        get() = _isQueryEmpty

    private var _showBottomSheet = MutableLiveData<Boolean>(false)
    val showBottomSheet: LiveData<Boolean>
        get() = _showBottomSheet

    private var _bottomSheetOptions = MutableLiveData<List<String>>(emptyList())
    val bottomSheetOptions: LiveData<List<String>>
        get() = _bottomSheetOptions

    private var _bottomOptionsListener =
        MutableLiveData<ItemSelectedListener>(ItemSelectedListener.emptyListener)
    val bottomOptionsListener: LiveData<ItemSelectedListener>
        get() = _bottomOptionsListener

    private var _bottomSheetTitle = MutableLiveData<String>("Title")
    val bottomSheetTitle: LiveData<String>
        get() = _bottomSheetTitle

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


    fun setSearchActive(isSearchActive: Boolean) {
        _isSearchActive.postValue(isSearchActive)
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

    private var _offlineMode = MutableLiveData<Boolean>(prefsRepo.offlineMode)
    val offlineMode: LiveData<Boolean>
        get() = _offlineMode

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
    }


    fun disableOfflineMode() {
        prefsRepo.offlineMode = false
        _books = bookRepository.getAllBooks()
    }

    override fun onCleared() {
        prefsRepo.unRegisterPrefsListener(offlineModeListener)
        super.onCleared()
    }

    /**
     * Prompt the user with a choice to download all audiobooks on device, presenting the user
     * with information on the space available in the sync directory and the space required
     */
    fun promptDownloadAll() {
        // Calculate space available
        val bytesAvailable = prefsRepo.cachedMediaDir.bytesAvailable()

        // Calculate space required
        viewModelScope.launch {
            val tracks = try {
                trackRepository.loadAllTracksAsync()
            } catch (e: Exception) {
                Log.e(APP_NAME, "Failed to load tracks!")
                emptyList<MediaItemTrack>()
            }
            var bytesToBeUsed = 0L
            tracks.forEach { bytesToBeUsed += it.size }
            val prompt = "Download will use ${Formatter.formatFileSize(
                Injector.get().applicationContext(),
                bytesToBeUsed
            )}. ${Formatter.formatFileSize(
                Injector.get().applicationContext(),
                bytesAvailable
            )} available"

            showOptionsMenu(prompt, listOf("Yes", "No"), object : ItemSelectedListener {
                override fun onItemSelected(itemName: String) {
                    when (itemName) {
                        "Yes" -> downloadAll(tracks)
                        "No" -> { }
                        else -> throw NoWhenBranchMatchedException()
                    }
                }
            })

        }

        // Prompt user to download
    }

    private fun downloadAll(tracks: List<MediaItemTrack>) {
        cachedFileManager.downloadTracks(tracks)
    }

    private fun showOptionsMenu(
        title: String,
        options: List<String>,
        listener: ItemSelectedListener
    ) {
        _showBottomSheet.postValue(true)
        _bottomSheetTitle.postValue(title)
        _bottomOptionsListener.postValue(listener)
        _bottomSheetOptions.postValue(options)
    }

}
