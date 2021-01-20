package io.github.mattpvaughn.chronicle.features.sources

import androidx.lifecycle.*
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.data.local.IBookRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository
import io.github.mattpvaughn.chronicle.data.sources.MediaSource
import io.github.mattpvaughn.chronicle.data.sources.MediaSource.Companion.NO_SOURCE_FOUND
import io.github.mattpvaughn.chronicle.data.sources.SourceManager
import io.github.mattpvaughn.chronicle.navigation.Navigator
import io.github.mattpvaughn.chronicle.util.Event
import io.github.mattpvaughn.chronicle.util.postEvent
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import javax.inject.Inject


@OptIn(InternalCoroutinesApi::class)
class SourceManagerViewModel @Inject constructor(
    private val sourceManager: SourceManager,
    private val navigator: Navigator,
    private val trackRepository: ITrackRepository,
    private val bookRepository: IBookRepository
) : ViewModel() {

    fun addSource() {
        navigator.addSource()
    }

    /** Deletes a [source] permanently if it exists */
    fun removeSource() {
        source.value?.id?.let { id ->
            if (id == NO_SOURCE_FOUND) {
                return@let
            }
            val result = sourceManager.removeSource(id)
            viewModelScope.launch {
                // TODO: Should be added to some sort of task to ensure completion. WorkManager?
                if (result.isSuccess) {
                    trackRepository.removeWithSource(id)
                    bookRepository.removeWithSource(id)
                }
            }
        }
        _source.postValue(OptionalMediaSourceWrapper())
    }

    fun showErrorMessage(message: String) {
        _userMessage.postEvent(message)
    }

    /** A light wrapper around [MediaSource] which provides default values if source is missing */
    data class OptionalMediaSourceWrapper(val source: MediaSource? = null) {

        val id = source?.id ?: NO_SOURCE_FOUND
        val type = source?.type() ?: "No type"
        val name = source?.name ?: "No source"
        val icon = source?.icon ?: R.drawable.ic_library_music

        val isEmpty = source == null

    }

    private val _source = MutableLiveData(OptionalMediaSourceWrapper())
    val source: LiveData<OptionalMediaSourceWrapper>
        get() = _source

    fun showEditSource(source: MediaSource) {
        _source.postValue(OptionalMediaSourceWrapper(source))
    }

    fun closeEditSource() {
        _source.postValue(OptionalMediaSourceWrapper())
    }

    class Factory @Inject constructor(
        private val sourceManager: SourceManager,
        private val navigator: Navigator,
        private val trackRepository: ITrackRepository,
        private val bookRepository: IBookRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SourceManagerViewModel::class.java)) {
                return SourceManagerViewModel(
                    sourceManager,
                    navigator,
                    trackRepository,
                    bookRepository
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewHolder class")
        }
    }

    private val _userMessage = MutableLiveData<Event<String>>()
    val userMessage: LiveData<Event<String>>
        get() = _userMessage

    val sources: LiveData<List<MediaSource>> = sourceManager.sourcesObservable

}