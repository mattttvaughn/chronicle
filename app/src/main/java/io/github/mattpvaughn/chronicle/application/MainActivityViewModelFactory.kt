package io.github.mattpvaughn.chronicle.application

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.mattpvaughn.chronicle.data.local.IBookRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import io.github.mattpvaughn.chronicle.data.plex.PlexConfig
import io.github.mattpvaughn.chronicle.features.player.MediaServiceConnection
import javax.inject.Inject

class MainActivityViewModelFactory @Inject constructor(
    private val trackRepository: ITrackRepository,
    private val bookRepository: IBookRepository,
    private val mediaServiceConnection: MediaServiceConnection,
    private val prefsRepo: PrefsRepo,
    private val plexConfig: PlexConfig
): ViewModelProvider.Factory{
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainActivityViewModel::class.java)) {
            return MainActivityViewModel(
                trackRepository,
                bookRepository,
                mediaServiceConnection,
                prefsRepo,
                plexConfig
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewHolder class")
    }
}