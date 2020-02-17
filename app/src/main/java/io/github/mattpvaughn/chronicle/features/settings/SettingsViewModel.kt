package io.github.mattpvaughn.chronicle.features.settings

import android.text.format.Formatter
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mattpvaughn.chronicle.BuildConfig
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.data.local.IBookRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME
import io.github.mattpvaughn.chronicle.data.plex.CachedFileManager
import io.github.mattpvaughn.chronicle.data.plex.PlexPrefsRepo
import io.github.mattpvaughn.chronicle.data.plex.PlexRequestSingleton
import io.github.mattpvaughn.chronicle.util.bytesAvailable
import io.github.mattpvaughn.chronicle.views.BottomSheetChooser
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException


class SettingsViewModel(
    private val bookRepository: IBookRepository,
    private val trackRepository: ITrackRepository,
    private val prefsRepo: PrefsRepo,
    private val plexPrefsRepo: PlexPrefsRepo,
    private val cachedFileManager: CachedFileManager
) : ViewModel() {

    private var _preferences = MutableLiveData<List<PreferenceModel>>(makePreferences())
    val preferences: LiveData<List<PreferenceModel>>
        get() = _preferences

    private var _bottomSheetOptions = MutableLiveData<List<String>>(emptyList())
    val bottomSheetOptions: LiveData<List<String>>
        get() = _bottomSheetOptions

    private var _bottomOptionsListener =
        MutableLiveData<BottomSheetChooser.ItemSelectedListener>(BottomSheetChooser.ItemSelectedListener.emptyListener)
    val bottomOptionsListener: LiveData<BottomSheetChooser.ItemSelectedListener>
        get() = _bottomOptionsListener

    private var _bottomSheetTitle = MutableLiveData<String>("Title")
    val bottomSheetTitle: LiveData<String>
        get() = _bottomSheetTitle

    private var _showBottomSheet = MutableLiveData<Boolean>(false)
    val showBottomSheet: LiveData<Boolean>
        get() = _showBottomSheet

    private var _messageForUser = MutableLiveData<String>()
    val messageForUser: LiveData<String>
        get() = _messageForUser

    private var _webLink = MutableLiveData<String>()
    val webLink: LiveData<String>
        get() = _webLink

    private var _returnToLogin = MutableLiveData<Boolean>(false)
    val returnToLogin: LiveData<Boolean>
        get() = _returnToLogin

    private var _showLicenseActivity = MutableLiveData<Boolean>(false)
    val showLicenseActivity: LiveData<Boolean>
        get() = _showLicenseActivity

    private fun showOptionsMenu(
        options: List<String>,
        title: String,
        listener: BottomSheetChooser.ItemSelectedListener
    ) {
        _showBottomSheet.postValue(true)
        _bottomSheetTitle.postValue(title)
        _bottomOptionsListener.postValue(listener)
        _bottomSheetOptions.postValue(options)
    }

    private fun makePreferences(): List<PreferenceModel> {
        return mutableListOf(
            PreferenceModel(PreferenceType.TITLE, "SYNC"),
            PreferenceModel(
                type = PreferenceType.CLICKABLE,
                title = "Sync Location",
                explanation = "Choose where synced books will be stored",
                click = object : PreferenceClick {
                    override fun onClick() {
                        showOptionsMenu(
                            options = Injector.get().externalDeviceDirs().map {
                                "${it.path}: ${Formatter.formatFileSize(
                                    Injector.get().applicationContext(),
                                    it.bytesAvailable()
                                )} free"
                            },
                            title = "Sync location",
                            listener = object : BottomSheetChooser.ItemSelectedListener {
                                override fun onItemSelected(itemName: String) {
                                    // Do something!
                                    val syncLoc = Injector.get().externalDeviceDirs().firstOrNull {
                                        itemName.contains(it.path)
                                    }
                                    if (syncLoc != null) {
                                        setSyncLocation(syncLoc)
                                    }
                                    _showBottomSheet.postValue(false)
                                }
                            })
                    }
                }),
            PreferenceModel(
                type = PreferenceType.CLICKABLE,
                title = "Deleted synced files",
                explanation = "Remove all downloaded files from your device",
                click = object : PreferenceClick {
                    override fun onClick() {
                        showOptionsMenu(
                            options = listOf("Yes", "No"),
                            title = "Delete all downloaded files?",
                            listener = object : BottomSheetChooser.ItemSelectedListener {
                                override fun onItemSelected(itemName: String) {
                                    when (itemName) {
                                        "Yes" -> {
                                            val deletedFileCount = cachedFileManager.uncacheAll()
                                            notifyUser("Deleted $deletedFileCount cached files")
                                        }
                                    }
                                    _showBottomSheet.postValue(false)
                                }

                            }

                        )
                    }
                }),
            PreferenceModel(
                PreferenceType.BOOLEAN,
                "Offline Mode",
                PrefsRepo.KEY_OFFLINE_MODE
            ),
            PreferenceModel(PreferenceType.TITLE, "Playback"),
            PreferenceModel(
                PreferenceType.BOOLEAN,
                "Skip silent audio",
                PrefsRepo.KEY_SKIP_SILENCE
            ),
            PreferenceModel(PreferenceType.TITLE, "Account"),
            PreferenceModel(
                PreferenceType.CLICKABLE,
                "Log out",
                click = object : PreferenceClick {
                    override fun onClick() {
                        cachedFileManager.uncacheAll()
                        PlexRequestSingleton.clear()
                        plexPrefsRepo.removeServer()
                        plexPrefsRepo.removeLibraryName()
                        plexPrefsRepo.removeAuthToken()
                        clearDatabase()
                        _returnToLogin.postValue(true)
                    }
                }),
            PreferenceModel(PreferenceType.TITLE, "Etc"),
            PreferenceModel(
                type = PreferenceType.CLICKABLE,
                title = "Licenses",
                explanation = "Open source libraries used by Chronicle",
                click = object : PreferenceClick {
                    override fun onClick() {
                        _showLicenseActivity.postValue(true)
                    }
                }),
            PreferenceModel(
                PreferenceType.CLICKABLE,
                title = "Hire me?",
                explanation = "Recent college grad looking for a software development job. Click for my website",
                click = object : PreferenceClick {
                    override fun onClick() {
                        _webLink.postValue("https://mattpvaughn.github.io")
                    }
                })
        )
            .apply {
                if (BuildConfig.DEBUG) {
                    this.addAll(
                        listOf(
                            PreferenceModel(PreferenceType.TITLE, "Developer options"),
                            PreferenceModel(
                                PreferenceType.CLICKABLE,
                                "Clear shared prefs",
                                click = object : PreferenceClick {
                                    override fun onClick() {
                                        prefsRepo.clearAll()
                                    }
                                }),
                            PreferenceModel(
                                PreferenceType.CLICKABLE,
                                "Clear DB",
                                click = object : PreferenceClick {
                                    override fun onClick() {
                                        clearDatabase()
                                    }
                                }),
                            PreferenceModel(
                                PreferenceType.BOOLEAN,
                                "Disable local progress tracking",
                                PrefsRepo.KEY_DEBUG_DISABLE_PROGRESS
                            )
                        )
                    )
                }
            }
    }

    /**
     * Sets future synced files to be downloaded to [syncDir] and moves existing synced files
     * to [syncDir]
     */
    private fun setSyncLocation(syncDir: File) {
        prefsRepo.cachedMediaDir = syncDir

        viewModelScope.launch {
            val deviceDirs = Injector.get().externalDeviceDirs().filter { it.path != syncDir.path }

            viewModelScope.launch {
                deviceDirs.forEach { dir ->
                    dir.listFiles { cachedFile ->
                        MediaItemTrack.cachedFilePattern.matches(cachedFile.name)
                    }.forEach { cachedFile ->
                        Log.i(APP_NAME, "Moving file ${cachedFile.absolutePath} to ${File(syncDir, cachedFile.name).absolutePath}")
                        try {
                            val copied = cachedFile.copyTo(File(syncDir, cachedFile.name), overwrite = true)
                            if (copied.exists()) {
                                cachedFile.delete()
                            }
                            Log.i(APP_NAME, "Moved file ${cachedFile.name}? ${copied.exists()}")
                        } catch (io: IOException) {
                            Log.i(APP_NAME, "IO exception occurred while changing sync location: $io")
                        }
                    }
                }
            }
        }
    }

    private fun clearDatabase() {
        viewModelScope.launch {
            bookRepository.clear()
            trackRepository.clear()
        }
    }

    private fun notifyUser(s: String) {
        _messageForUser.postValue(s)
    }

    fun setShowLicenseActivity(showLicense: Boolean) {
        _showLicenseActivity.postValue(showLicense)
    }

}
