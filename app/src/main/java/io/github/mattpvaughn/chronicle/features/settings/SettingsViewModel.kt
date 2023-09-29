package io.github.mattpvaughn.chronicle.features.settings

import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.text.format.Formatter
import androidx.lifecycle.*
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.facebook.drawee.backends.pipeline.Fresco
import io.github.mattpvaughn.chronicle.BuildConfig
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.application.FEATURE_FLAG_IS_AUTO_ENABLED
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.data.local.CollectionsRepository
import io.github.mattpvaughn.chronicle.data.local.IBookRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import io.github.mattpvaughn.chronicle.data.sources.plex.ICachedFileManager
import io.github.mattpvaughn.chronicle.data.sources.plex.IPlexLoginRepo
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexConfig
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexPrefsRepo
import io.github.mattpvaughn.chronicle.features.download.MoveSyncLocationWorker
import io.github.mattpvaughn.chronicle.features.player.MediaServiceConnection
import io.github.mattpvaughn.chronicle.features.settings.SettingsViewModel.NavigationDestination.*
import io.github.mattpvaughn.chronicle.util.Event
import io.github.mattpvaughn.chronicle.util.bytesAvailable
import io.github.mattpvaughn.chronicle.util.postEvent
import io.github.mattpvaughn.chronicle.views.BottomSheetChooser.*
import io.github.mattpvaughn.chronicle.views.BottomSheetChooser.BottomChooserState.Companion.EMPTY_BOTTOM_CHOOSER
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * Represents the UI state of the settings screen. Responsible for loading and displaying
 * [PreferenceModel]s.
 *
 * Note: not using the built-in [PreferenceFragment] because making custom preferences is horrible
 *       and custom views or pop-ups are fine. This could be improved, though, it's quite indented
 *
 * TODO: Quite a bit of repetition of information in [PreferenceModel], making typos more likely.
 *       Might be worthwhile to look into alternatives used by other apps?
 */
class SettingsViewModel(
    private val bookRepository: IBookRepository,
    private val trackRepository: ITrackRepository,
    private val mediaServiceConnection: MediaServiceConnection,
    private val prefsRepo: PrefsRepo,
    private val plexLoginRepo: IPlexLoginRepo,
    private val cachedFileManager: ICachedFileManager,
    private val plexConfig: PlexConfig,
    private val workManager: WorkManager,
    private val plexPrefs: PlexPrefsRepo,
    private val collectionsRepository: CollectionsRepository
) : ViewModel() {

    @Suppress("UNCHECKED_CAST")
    class Factory @Inject constructor(
        private val bookRepository: IBookRepository,
        private val trackRepository: ITrackRepository,
        private val prefsRepo: PrefsRepo,
        private val mediaServiceConnection: MediaServiceConnection,
        private val plexLoginRepo: IPlexLoginRepo,
        private val cachedFileManager: ICachedFileManager,
        private val plexConfig: PlexConfig,
        private val workManager: WorkManager,
        private val plexPrefs: PlexPrefsRepo,
        private val collectionsRepository: CollectionsRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                return SettingsViewModel(
                    bookRepository = bookRepository,
                    trackRepository = trackRepository,
                    mediaServiceConnection = mediaServiceConnection,
                    prefsRepo = prefsRepo,
                    plexLoginRepo = plexLoginRepo,
                    cachedFileManager = cachedFileManager,
                    plexConfig = plexConfig,
                    workManager = workManager,
                    plexPrefs = plexPrefs,
                    collectionsRepository = collectionsRepository
                ) as T
            } else {
                throw IllegalArgumentException("Cannot instantiate $modelClass from SettingsViewModel.Factory")
            }
        }
    }

    private var _preferences = MutableLiveData(makePreferences())
    val preferences: LiveData<List<PreferenceModel>>
        get() = _preferences

    private var _bottomChooserState = MutableLiveData(EMPTY_BOTTOM_CHOOSER)
    val bottomChooserState: LiveData<BottomChooserState>
        get() = _bottomChooserState

    fun setBottomSheetVisibility(shouldShow: Boolean) {
        bottomChooserState.value?.let {
            _bottomChooserState.postValue(it.copy(shouldShow = shouldShow))
        }
    }

    private var _messageForUser = MutableLiveData<Event<FormattableString>>()
    val messageForUser: LiveData<Event<FormattableString>>
        get() = _messageForUser

    private var _webLink = MutableLiveData<Event<String>>()
    val webLink: LiveData<Event<String>>
        get() = _webLink

    private var _showLicenseActivity = MutableLiveData(false)
    val showLicenseActivity: LiveData<Boolean>
        get() = _showLicenseActivity

    private fun showOptionsMenu(
        options: List<FormattableString>,
        title: FormattableString,
        listener: BottomChooserListener
    ) {
        _bottomChooserState.postValue(
            BottomChooserState(
                options = options,
                title = title,
                listener = listener,
                shouldShow = true
            )
        )
    }

    private var _upgradeToPremium = MutableLiveData<Event<Unit>>()
    val upgradeToPremium: LiveData<Event<Unit>>
        get() = _upgradeToPremium

    private val prefsListener = OnSharedPreferenceChangeListener { _, _ ->
        // Rebuild the prefs list whenever any prefs change
        _preferences.postValue(makePreferences())
    }

    init {
        prefsRepo.registerPrefsListener(prefsListener)
    }

    override fun onCleared() {
        prefsRepo.unregisterPrefsListener(prefsListener)
    }

    fun startUpgradeToPremiumFlow() {
        _upgradeToPremium.postEvent(Unit)
    }

    private fun makePreferences(): List<PreferenceModel> {
        val list = mutableListOf(
            PreferenceModel(
                PreferenceType.TITLE,
                FormattableString.from(R.string.settings_premium_upgrade_label)
            ),
            if (prefsRepo.isPremium) {
                PreferenceModel(
                    type = PreferenceType.CLICKABLE,
                    title = FormattableString.from(R.string.settings_premium_unlocked_title),
                    explanation = FormattableString.from(R.string.settings_premium_unlocked_explanation)
                )
            } else {
                PreferenceModel(
                    type = PreferenceType.CLICKABLE,
                    title = FormattableString.from(R.string.settings_premium_upgrade_label),
                    explanation = FormattableString.from(R.string.settings_premium_upgrade_explanation),
                    click = object : PreferenceClick {
                        override fun onClick() {
                            startUpgradeToPremiumFlow()
                        }
                    }
                )
            },
            PreferenceModel(
                PreferenceType.TITLE,
                FormattableString.from(R.string.settings_category_appearance)
            ),
            PreferenceModel(
                type = PreferenceType.CLICKABLE,
                title = FormattableString.ResourceString(
                    stringRes = R.string.settings_book_cover_type_value,
                    placeHolderStrings = listOf(prefsRepo.bookCoverStyle)
                ),
                explanation = FormattableString.from(R.string.settings_book_cover_type_explanation),
                click = object : PreferenceClick {
                    override fun onClick() {
                        showOptionsMenu(
                            options = listOf(
                                FormattableString.from(R.string.settings_book_cover_type_square),
                                FormattableString.from(R.string.settings_book_cover_type_rect)
                            ),
                            title = FormattableString.from(R.string.settings_book_cover_type_label),
                            listener = object : BottomChooserItemListener() {
                                override fun onItemClicked(formattableString: FormattableString) {
                                    check(formattableString is FormattableString.ResourceString)

                                    when (formattableString.stringRes) {
                                        R.string.settings_book_cover_type_rect -> {
                                            prefsRepo.bookCoverStyle = "Rectangle"
                                        }
                                        R.string.settings_book_cover_type_square -> {
                                            prefsRepo.bookCoverStyle = "Square"
                                        }
                                        else -> throw NoWhenBranchMatchedException("Unknown book cover type")
                                    }
                                    setBottomSheetVisibility(false)
                                }
                            }
                        )
                    }
                }
            ),
            PreferenceModel(
                PreferenceType.TITLE,
                FormattableString.from(R.string.settings_category_sync)
            ),
            PreferenceModel(
                type = PreferenceType.CLICKABLE,
                title = FormattableString.ResourceString(
                    stringRes = R.string.settings_refresh_rate_value,
                    // feels gross
                    placeHolderStrings = listOf(
                        when {
                            prefsRepo.refreshRateMinutes == 0L -> {
                                Injector.get()
                                    .applicationContext().resources.getString(R.string.settings_refresh_rate_always)
                            }
                            prefsRepo.refreshRateMinutes < 60 -> {
                                "${prefsRepo.refreshRateMinutes} " + Injector.get()
                                    .applicationContext().resources.getString(R.string.minutes)
                            }
                            prefsRepo.refreshRateMinutes < 60 * 24 -> {
                                "${prefsRepo.refreshRateMinutes / 60} " + Injector.get()
                                    .applicationContext().resources.getString(R.string.hours)
                            }
                            prefsRepo.refreshRateMinutes <= 60 * 24 * 7 -> {
                                "${prefsRepo.refreshRateMinutes / (60 * 24)} " + Injector.get()
                                    .applicationContext().resources.getString(R.string.days)
                            }
                            prefsRepo.refreshRateMinutes > 60 * 24 * 7 -> {
                                Injector.get()
                                    .applicationContext().resources.getString(R.string.settings_refresh_rate_manual)
                            }
                            else -> throw NoWhenBranchMatchedException()
                        }
                    )
                ),
                explanation = FormattableString.from(R.string.settings_refresh_rate_explanation),
                click = object : PreferenceClick {
                    override fun onClick() {
                        showOptionsMenu(
                            options = listOf(
                                FormattableString.from(R.string.settings_refresh_rate_always),
                                FormattableString.from(R.string.settings_refresh_rate_15_minutes),
                                FormattableString.from(R.string.settings_refresh_rate_1_hour),
                                FormattableString.from(R.string.settings_refresh_rate_3_hours),
                                FormattableString.from(R.string.settings_refresh_rate_6_hours),
                                FormattableString.from(R.string.settings_refresh_rate_1_day),
                                FormattableString.from(R.string.settings_refresh_rate_3_days),
                                FormattableString.from(R.string.settings_refresh_rate_1_week),
                                FormattableString.from(R.string.settings_refresh_rate_manual)
                            ),
                            title = FormattableString.from(R.string.settings_refresh_rate_title),
                            listener = object : BottomChooserItemListener() {
                                override fun onItemClicked(formattableString: FormattableString) {
                                    check(formattableString is FormattableString.ResourceString)
                                    when (formattableString.stringRes) {
                                        R.string.settings_refresh_rate_always ->
                                            prefsRepo.refreshRateMinutes =
                                                0
                                        R.string.settings_refresh_rate_15_minutes ->
                                            prefsRepo.refreshRateMinutes =
                                                15
                                        R.string.settings_refresh_rate_1_hour ->
                                            prefsRepo.refreshRateMinutes =
                                                60
                                        R.string.settings_refresh_rate_3_hours ->
                                            prefsRepo.refreshRateMinutes =
                                                180
                                        R.string.settings_refresh_rate_6_hours ->
                                            prefsRepo.refreshRateMinutes =
                                                360
                                        R.string.settings_refresh_rate_1_day ->
                                            prefsRepo.refreshRateMinutes =
                                                60 * 24
                                        R.string.settings_refresh_rate_3_days ->
                                            prefsRepo.refreshRateMinutes =
                                                60 * 24 * 3
                                        R.string.settings_refresh_rate_1_week ->
                                            prefsRepo.refreshRateMinutes =
                                                60 * 24 * 7
                                        R.string.settings_refresh_rate_manual ->
                                            prefsRepo.refreshRateMinutes =
                                                Long.MAX_VALUE
                                        else -> throw NoWhenBranchMatchedException("Unknown item: ${formattableString.stringRes}")
                                    }
                                    setBottomSheetVisibility(false)
                                }
                            }
                        )
                    }
                }
            ),
            PreferenceModel(
                type = PreferenceType.CLICKABLE,
                title = FormattableString.ResourceString(
                    stringRes = R.string.settings_sync_location_value,
                    placeHolderStrings = listOf(
                        Formatter.formatFileSize(
                            Injector.get().applicationContext(),
                            prefsRepo.cachedMediaDir.bytesAvailable()
                        )
                    )
                ),
                explanation = FormattableString.from(R.string.settings_sync_location_explanation),
                click = object : PreferenceClick {
                    override fun onClick() {
                        showOptionsMenu(
                            options = Injector.get().externalDeviceDirs().map {
                                FormattableString.ResourceString(
                                    stringRes = R.string.settings_sync_space_available,
                                    placeHolderStrings = listOf(
                                        it.path,
                                        Formatter.formatFileSize(
                                            Injector.get().applicationContext(),
                                            it.bytesAvailable()
                                        )
                                    )
                                )
                            },
                            title = FormattableString.from(R.string.settings_sync_location_title),
                            listener = object : BottomChooserItemListener() {
                                override fun onItemClicked(formattableString: FormattableString) {
                                    check(formattableString is FormattableString.ResourceString)

                                    val chosen = formattableString.placeHolderStrings[0]
                                    val syncLoc = Injector.get().externalDeviceDirs().firstOrNull {
                                        chosen.contains(it.path)
                                    }
                                    if (syncLoc != null) {
                                        setSyncLocation(syncLoc)
                                    }
                                    setBottomSheetVisibility(false)
                                }
                            }
                        )
                    }
                }
            ),
            PreferenceModel(
                type = PreferenceType.CLICKABLE,
                title = FormattableString.from(R.string.settings_delete_synced_title),
                explanation = FormattableString.from(R.string.settings_delete_synced_explanation),
                click = object : PreferenceClick {
                    override fun onClick() {
                        showOptionsMenu(
                            options = listOf(FormattableString.yes, FormattableString.no),
                            title = FormattableString.from(R.string.settings_delete_synced_confirm),
                            listener = object : BottomChooserItemListener() {
                                override fun onItemClicked(formattableString: FormattableString) {
                                    when (formattableString) {
                                        FormattableString.yes -> {
                                            viewModelScope.launch {
                                                val deletedFileCount =
                                                    cachedFileManager.uncacheAllInLibrary()
                                                showUserMessage(
                                                    FormattableString.ResourceString(
                                                        R.string.settings_delete_synced_response,
                                                        placeHolderStrings = listOf(deletedFileCount.toString())
                                                    )
                                                )
                                            }
                                        }
                                        else -> {
                                        } /* do nothing*/
                                    }
                                    setBottomSheetVisibility(false)
                                }
                            }
                        )
                    }
                }
            ),
            PreferenceModel(
                PreferenceType.BOOLEAN,
                FormattableString.from(R.string.settings_offline_mode_title),
                PrefsRepo.KEY_OFFLINE_MODE,
                defaultValue = prefsRepo.offlineMode
            ),
            PreferenceModel(
                PreferenceType.TITLE,
                FormattableString.from(R.string.settings_category_playback)
            ),
            PreferenceModel(
                PreferenceType.BOOLEAN,
                FormattableString.from(R.string.settings_skip_silent_audio),
                PrefsRepo.KEY_SKIP_SILENCE,
                defaultValue = prefsRepo.skipSilence
            ),
            PreferenceModel(
                PreferenceType.BOOLEAN,
                FormattableString.from(R.string.settings_auto_rewind),
                PrefsRepo.KEY_AUTO_REWIND_ENABLED,
                FormattableString.from(R.string.settings_auto_rewind_explanation),
                defaultValue = prefsRepo.autoRewind
            ),
            PreferenceModel(
                type = PreferenceType.BOOLEAN,
                title = FormattableString.from(R.string.settings_shake_to_snooze_title),
                explanation = FormattableString.from(R.string.settings_shake_to_snooze_explanation),
                key = PrefsRepo.KEY_SHAKE_TO_SNOOZE_ENABLED,
                defaultValue = prefsRepo.shakeToSnooze
            ),
            PreferenceModel(
                type = PreferenceType.BOOLEAN,
                title = FormattableString.from(R.string.settings_pause_on_focus_lost_title),
                explanation = FormattableString.from(R.string.settings_pause_on_focus_lost_explanation),
                key = PrefsRepo.KEY_PAUSE_ON_FOCUS_LOST,
                defaultValue = prefsRepo.pauseOnFocusLost
            ),
            PreferenceModel(
                type = PreferenceType.CLICKABLE,
                title = FormattableString.ResourceString(
                    stringRes = R.string.settings_jump_forward_value,
                    // feels gross
                    placeHolderStrings = listOf(
                        "${prefsRepo.jumpForwardSeconds} " + Injector.get()
                            .applicationContext().resources.getString(R.string.seconds)
                    )
                ),
                explanation = FormattableString.from(R.string.settings_jump_forward_explanation),
                click = object : PreferenceClick {
                    override fun onClick() {
                        showOptionsMenu(
                            options = listOf(
                                FormattableString.from(R.string.settings_jump_10_seconds),
                                FormattableString.from(R.string.settings_jump_15_seconds),
                                FormattableString.from(R.string.settings_jump_20_seconds),
                                FormattableString.from(R.string.settings_jump_30_seconds),
                                FormattableString.from(R.string.settings_jump_60_seconds),
                                FormattableString.from(R.string.settings_jump_90_seconds)
                            ),
                            title = FormattableString.from(R.string.settings_jump_forward_title),
                            listener = object : BottomChooserItemListener() {
                                override fun onItemClicked(formattableString: FormattableString) {
                                    check(formattableString is FormattableString.ResourceString)
                                    prefsRepo.jumpForwardSeconds = when (formattableString.stringRes) {
                                        R.string.settings_jump_10_seconds -> 10L
                                        R.string.settings_jump_15_seconds -> 15L
                                        R.string.settings_jump_20_seconds -> 20L
                                        R.string.settings_jump_30_seconds -> 30L
                                        R.string.settings_jump_60_seconds -> 60L
                                        R.string.settings_jump_90_seconds -> 90L
                                        else -> 30L
                                    }
                                    setBottomSheetVisibility(false)
                                }
                            }
                        )
                    }
                }
            ),
            PreferenceModel(
                type = PreferenceType.CLICKABLE,
                title = FormattableString.ResourceString(
                    stringRes = R.string.settings_jump_backward_value,
                    // feels gross
                    placeHolderStrings = listOf(
                        "${prefsRepo.jumpBackwardSeconds} " + Injector.get()
                            .applicationContext().resources.getString(R.string.seconds)
                    )
                ),
                explanation = FormattableString.from(R.string.settings_jump_backward_explanation),
                click = object : PreferenceClick {
                    override fun onClick() {
                        showOptionsMenu(
                            options = listOf(
                                FormattableString.from(R.string.settings_jump_10_seconds),
                                FormattableString.from(R.string.settings_jump_15_seconds),
                                FormattableString.from(R.string.settings_jump_20_seconds),
                                FormattableString.from(R.string.settings_jump_30_seconds),
                                FormattableString.from(R.string.settings_jump_60_seconds),
                                FormattableString.from(R.string.settings_jump_90_seconds)
                            ),
                            title = FormattableString.from(R.string.settings_jump_backward_title),
                            listener = object : BottomChooserItemListener() {
                                override fun onItemClicked(formattableString: FormattableString) {
                                    check(formattableString is FormattableString.ResourceString)
                                    prefsRepo.jumpBackwardSeconds = when (formattableString.stringRes) {
                                        R.string.settings_jump_10_seconds -> 10L
                                        R.string.settings_jump_15_seconds -> 15L
                                        R.string.settings_jump_20_seconds -> 20L
                                        R.string.settings_jump_30_seconds -> 30L
                                        R.string.settings_jump_60_seconds -> 60L
                                        R.string.settings_jump_90_seconds -> 90L
                                        else -> 10L
                                    }
                                    setBottomSheetVisibility(false)
                                }
                            }
                        )
                    }
                }
            ),
            PreferenceModel(
                PreferenceType.TITLE,
                FormattableString.from(R.string.settings_category_account)
            ),
            PreferenceModel(
                PreferenceType.CLICKABLE,
                title = FormattableString.from(R.string.settings_change_library),
                explanation = FormattableString.ResourceString(
                    R.string.settings_current_library,
                    listOf(plexPrefs.library?.name ?: "")
                ),
                click = object : PreferenceClick {
                    override fun onClick() {
                        viewModelScope.launch {
                            if (!cachedFileManager.hasUserCachedTracks()) {
                                clearConfig(RETURN_TO_LIBRARY_CHOOSER)
                                return@launch
                            }
                            showOptionsMenu(
                                title = FormattableString.from(R.string.prompt_clear_downloads_allow_retain),
                                options = listOf(FormattableString.yes, FormattableString.no),
                                listener = object : BottomChooserItemListener() {
                                    override fun onItemClicked(formattableString: FormattableString) {
                                        check(formattableString is FormattableString.ResourceString)
                                        if (formattableString.stringRes == R.string.yes) {
                                            // Keep downloaded
                                            clearConfig(
                                                RETURN_TO_LIBRARY_CHOOSER,
                                                clearDownloads = false
                                            )
                                        } else {
                                            // Delete downloaded
                                            clearConfig(
                                                RETURN_TO_LIBRARY_CHOOSER,
                                                clearDownloads = true
                                            )
                                        }
                                        setBottomSheetVisibility(false)
                                    }
                                }
                            )
                        }
                    }
                }
            ),
            PreferenceModel(
                PreferenceType.CLICKABLE,
                title = FormattableString.from(R.string.settings_change_server),
                explanation = FormattableString.ResourceString(
                    R.string.settings_current_server,
                    listOf(plexPrefs.server?.name ?: "")
                ),
                click = object : PreferenceClick {
                    override fun onClick() {
                        viewModelScope.launch {
                            if (!cachedFileManager.hasUserCachedTracks()) {
                                clearConfig(RETURN_TO_SERVER_CHOOSER)
                                return@launch
                            }
                            showOptionsMenu(
                                title = FormattableString.from(R.string.settings_clear_downloads_warning),
                                options = listOf(FormattableString.yes, FormattableString.no),
                                listener = object : BottomChooserItemListener() {
                                    override fun onItemClicked(formattableString: FormattableString) {
                                        if (formattableString == FormattableString.yes) {
                                            clearConfig(RETURN_TO_SERVER_CHOOSER)
                                        }
                                        setBottomSheetVisibility(false)
                                    }
                                }
                            )
                        }
                    }
                }
            ),
            PreferenceModel(
                PreferenceType.CLICKABLE,
                title = FormattableString.from(R.string.settings_change_user),
                explanation = FormattableString.ResourceString(
                    R.string.settings_current_user,
                    listOf(plexPrefs.user?.username ?: "")
                ),
                click = object : PreferenceClick {
                    override fun onClick() {
                        viewModelScope.launch {
                            if (!cachedFileManager.hasUserCachedTracks()) {
                                clearConfig(RETURN_TO_USER_CHOOSER)
                                return@launch
                            }
                            showOptionsMenu(
                                title = FormattableString.from(R.string.settings_clear_downloads_warning),
                                options = listOf(FormattableString.yes, FormattableString.no),
                                listener = object : BottomChooserItemListener() {
                                    override fun onItemClicked(formattableString: FormattableString) {
                                        if (formattableString == FormattableString.yes) {
                                            clearConfig(RETURN_TO_USER_CHOOSER)
                                        }
                                        setBottomSheetVisibility(false)
                                    }
                                }
                            )
                        }
                    }
                }
            ),
            PreferenceModel(
                PreferenceType.CLICKABLE,
                title = FormattableString.from(R.string.settings_log_out),
                click = object : PreferenceClick {
                    override fun onClick() {
                        viewModelScope.launch {
                            val logout = {
                                viewModelScope.launch {
                                    cachedFileManager.uncacheAllInLibrary()
                                }
                                plexConfig.clear()
                                mediaServiceConnection.transportControls?.stop()
                                clearConfig(RETURN_TO_LOGIN)
                            }
                            if (!cachedFileManager.hasUserCachedTracks()) {
                                logout()
                                return@launch
                            }
                            showOptionsMenu(
                                title = FormattableString.from(R.string.settings_clear_downloads_warning),
                                options = listOf(FormattableString.yes, FormattableString.no),
                                listener = object : BottomChooserItemListener() {
                                    override fun onItemClicked(formattableString: FormattableString) {
                                        if (formattableString == FormattableString.yes) {
                                            logout()
                                        }
                                        setBottomSheetVisibility(false)
                                    }
                                }
                            )
                        }
                        Timber.i("Logging out")
                    }
                }
            ),
            PreferenceModel(
                PreferenceType.TITLE,
                FormattableString.from(R.string.settings_category_etc)
            ),
            PreferenceModel(
                type = PreferenceType.CLICKABLE,
                title = FormattableString.from(R.string.settings_subreddit_title),
                explanation = FormattableString.from(R.string.settings_subreddit_explanation),
                click = object : PreferenceClick {
                    override fun onClick() {
                        _webLink.postEvent("https://www.reddit.com/r/ChronicleApp")
                    }
                }
            ),
            PreferenceModel(
                type = PreferenceType.CLICKABLE,
                title = FormattableString.from(R.string.settings_github_title),
                explanation = FormattableString.from(R.string.settings_github_explanation),
                click = object : PreferenceClick {
                    override fun onClick() {
                        _webLink.postEvent("https://github.com/mattttvaughn/chronicle")
                    }
                }
            ),
            PreferenceModel(
                type = PreferenceType.CLICKABLE,
                title = FormattableString.from(R.string.settings_version_title),
                explanation = FormattableString.from(BuildConfig.VERSION_NAME),
            ),
            PreferenceModel(
                type = PreferenceType.CLICKABLE,
                title = FormattableString.from(R.string.settings_licenses_title),
                explanation = FormattableString.from(R.string.settings_licenses_explanation),
                click = object : PreferenceClick {
                    override fun onClick() {
                        _showLicenseActivity.postValue(true)
                    }
                }
            )
        )

        if (BuildConfig.DEBUG) {
            list.addAll(
                listOf(
                    PreferenceModel(
                        PreferenceType.TITLE,
                        FormattableString.from(string = "Developer options")
                    ),
                    PreferenceModel(
                        PreferenceType.CLICKABLE,
                        FormattableString.from(string = "Clear shared prefs"),
                        click = object : PreferenceClick {
                            override fun onClick() {
                                prefsRepo.clearAll()
                            }
                        }
                    ),
                    PreferenceModel(
                        PreferenceType.CLICKABLE,
                        FormattableString.from(string = "Clear DB"),
                        click = object : PreferenceClick {
                            override fun onClick() {
                                clearConfig(clearDownloads = false)
                            }
                        }
                    ),
                    PreferenceModel(
                        PreferenceType.CLICKABLE,
                        FormattableString.from(string = "Clear cached images"),
                        click = object : PreferenceClick {
                            override fun onClick() {
                                viewModelScope.launch {
                                    withContext(Dispatchers.IO) {
                                        Fresco.getImagePipeline().clearCaches()
                                    }
                                }
                            }
                        }
                    ),
                    PreferenceModel(
                        PreferenceType.BOOLEAN,
                        FormattableString.from(string = "Disable local progress tracking"),
                        PrefsRepo.KEY_DEBUG_DISABLE_PROGRESS,
                        defaultValue = false
                    )
                )
            )
        }

        if (FEATURE_FLAG_IS_AUTO_ENABLED) {
            val autoRewindPref = list.find { it.key == PrefsRepo.KEY_AUTO_REWIND_ENABLED }
            if (autoRewindPref != null) {
                val insertIndex = list.indexOf(autoRewindPref)
                if (insertIndex != -1) {
                    list.add(
                        insertIndex + 1,
                        PreferenceModel(
                            type = PreferenceType.BOOLEAN,
                            title = FormattableString.from(R.string.allow_auto),
                            explanation = FormattableString.from(R.string.allow_auto_explanation),
                            key = PrefsRepo.KEY_ALLOW_AUTO,
                            defaultValue = prefsRepo.allowAuto
                        )
                    )
                }
            }
        }

        return list
    }

    /**
     * Sets future synced files to be downloaded to [syncDir] and moves existing synced files
     * to [syncDir]
     */
    private fun setSyncLocation(syncDir: File) {
        prefsRepo.cachedMediaDir = syncDir

        val worker = OneTimeWorkRequestBuilder<MoveSyncLocationWorker>().build()

        workManager.beginUniqueWork(
            MoveSyncLocationWorker.WORKER_ID,
            ExistingWorkPolicy.REPLACE,
            worker
        ).enqueue()
    }

    private enum class NavigationDestination {
        RETURN_TO_LIBRARY_CHOOSER,
        RETURN_TO_SERVER_CHOOSER,
        RETURN_TO_LOGIN,
        RETURN_TO_USER_CHOOSER,
        DO_NOT_NAVIGATE
    }

    /**
     * Clears the server cached data, and navigates to reset the data on a chooser depending on the
     * [navigateTo] provided
     */
    private fun clearConfig(
        navigateTo: NavigationDestination = DO_NOT_NAVIGATE,
        clearDownloads: Boolean = true
    ) {
        viewModelScope.launch(Injector.get().unhandledExceptionHandler()) {
            if (clearDownloads) {
                cachedFileManager.uncacheAllInLibrary()
            }
            withContext(Dispatchers.IO) {
                bookRepository.clear()
                trackRepository.clear()
                collectionsRepository.clear()
            }
            mediaServiceConnection.transportControls?.stop()
            when (navigateTo) {
                RETURN_TO_LIBRARY_CHOOSER -> plexConfig.clearLibrary()
                RETURN_TO_SERVER_CHOOSER -> plexConfig.clearServer()
                RETURN_TO_LOGIN -> plexConfig.clear()
                RETURN_TO_USER_CHOOSER -> plexConfig.clearUser()
                DO_NOT_NAVIGATE -> {
                }
            }
            plexLoginRepo.determineLoginState()
        }
    }

    fun showUserMessage(formattableString: FormattableString) {
        _messageForUser.postEvent(formattableString)
    }

    fun setShowLicenseActivity(showLicense: Boolean) {
        _showLicenseActivity.postValue(showLicense)
    }
}
