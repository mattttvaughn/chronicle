package io.github.mattpvaughn.chronicle.features.sources

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.mattpvaughn.chronicle.data.sources.MediaSource
import io.github.mattpvaughn.chronicle.data.sources.SourceManager
import io.github.mattpvaughn.chronicle.util.Event
import kotlinx.coroutines.InternalCoroutinesApi
import javax.inject.Inject


@OptIn(InternalCoroutinesApi::class)
class SourceManagerViewModel @Inject constructor(
    private val sourceManager: SourceManager
) : ViewModel() {

    class Factory @Inject constructor(private val sourceManager: SourceManager) :
        ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SourceManagerViewModel::class.java)) {
                return SourceManagerViewModel(sourceManager) as T
            }
            throw IllegalArgumentException("Unknown ViewHolder class")
        }
    }

    private val _userMessage = MutableLiveData<Event<String>>()
    val userMessage: LiveData<Event<String>>
        get() = _userMessage

    private var _sources = MutableLiveData<List<MediaSource>>(sourceManager.getSources())
    val sources: LiveData<List<MediaSource>>
        get() = _sources

}