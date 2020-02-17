package io.github.mattpvaughn.chronicle.application

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.mattpvaughn.chronicle.data.local.IBookRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository
import io.github.mattpvaughn.chronicle.data.plex.PlexPrefsRepo
import io.github.mattpvaughn.chronicle.features.player.MediaServiceConnection
import io.github.mattpvaughn.chronicle.features.settings.PrefsRepo
import javax.inject.Inject

class MainActivityViewModelFactory @Inject constructor(
    private val trackRepository: ITrackRepository,
    private val bookRepository: IBookRepository,
    private val mediaServiceConnection: MediaServiceConnection,
    private val plexPrefsRepo: PlexPrefsRepo,
    private val prefsRepo: PrefsRepo
): ViewModelProvider.Factory{
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainActivityViewModel::class.java)) {
            return MainActivityViewModel(
                trackRepository,
                bookRepository,
                mediaServiceConnection,
                plexPrefsRepo,
                prefsRepo
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewHolder class")
    }
}