package io.github.mattpvaughn.chronicle.features.login

import androidx.lifecycle.*
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.data.model.LoadingStatus
import io.github.mattpvaughn.chronicle.data.sources.plex.IPlexLoginRepo
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexLoginService
import io.github.mattpvaughn.chronicle.data.sources.plex.model.PlexUser
import io.github.mattpvaughn.chronicle.util.Event
import io.github.mattpvaughn.chronicle.util.postEvent
import kotlinx.coroutines.launch
import retrofit2.HttpException
import timber.log.Timber
import javax.inject.Inject

class ChooseUserViewModel(
    private val plexLoginService: PlexLoginService,
    private val plexLoginRepo: IPlexLoginRepo
) : ViewModel() {

    class Factory @Inject constructor(
        private val plexLoginService: PlexLoginService,
        private val plexLoginRepo: IPlexLoginRepo
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ChooseUserViewModel::class.java)) {
                return ChooseUserViewModel(plexLoginService, plexLoginRepo) as T
            }
            throw IllegalArgumentException("Unknown ViewHolder class")
        }
    }

    private val _userMessage = MutableLiveData<Event<String>>()
    val userMessage: LiveData<Event<String>>
        get() = _userMessage

    private val _showPin = MutableLiveData(false)
    val showPin: LiveData<Boolean>
        get() = _showPin

    private val _user = MutableLiveData<PlexUser>()
    val user: LiveData<PlexUser>
        get() = _user

    private val _pinData = MutableLiveData<String>()
    private val pinData: LiveData<String>
        get() = _pinData

    private val _pinErrorMessage = MutableLiveData<String?>(null)
    val pinErrorMessage: LiveData<String?>
        get() = _pinErrorMessage

    private var _users = MutableLiveData(emptyList<PlexUser>())
    val users: LiveData<List<PlexUser>>
        get() = _users

    private var _usersLoadingStatus = MutableLiveData(LoadingStatus.LOADING)
    val usersLoadingStatus: LiveData<LoadingStatus>
        get() = _usersLoadingStatus

    private var _pinLoadingStatus = MutableLiveData(LoadingStatus.DONE)
    val pinLoadingStatus: LiveData<LoadingStatus>
        get() = _pinLoadingStatus

    init {
        loadUsers()
    }

    private fun loadUsers(forceLoad: Boolean = false) {
        // Don't override current users unless we explicitly force a reload
        if (!users.value.isNullOrEmpty() && !forceLoad) {
            return
        }
        viewModelScope.launch(Injector.get().unhandledExceptionHandler()) {
            try {
                _usersLoadingStatus.value = LoadingStatus.LOADING
                val usersResponse = plexLoginService.getUsersForAccount()
                _users.postValue(usersResponse.users)
                _usersLoadingStatus.value = LoadingStatus.DONE
            } catch (e: Throwable) {
                Timber.e("Failed to get users: $e")
                _userMessage.postEvent("Failed to load users: ${e.message}")
                _usersLoadingStatus.value = LoadingStatus.ERROR
            }
        }
    }

    fun refresh() {
        loadUsers(forceLoad = true)
    }

    fun submitPin() {
        viewModelScope.launch {
            user.value?.uuid?.let { uuid ->
                submitPin(uuid, pinData.value)
            }
        }
    }

    fun pickUser(user: PlexUser) {
        // If a pin is required before a query can be sent off, show the pin now
        _user.postValue(user)
        if (user.hasPassword) {
            _showPin.postValue(true)
        } else {
            viewModelScope.launch {
                submitPin(user.uuid, null)
            }
        }
    }

    private suspend fun submitPin(uuid: String, pin: String?) {
        try {
            _pinLoadingStatus.postValue(LoadingStatus.LOADING)
            val responseUser: PlexUser = plexLoginService.pickUser(uuid, pin)
            if (responseUser.authToken.isNullOrEmpty()) {
                throw IllegalStateException("Pin submitted but no auth token received")
            }
            plexLoginRepo.chooseUser(responseUser)
            _pinLoadingStatus.postValue(LoadingStatus.DONE)
        } catch (t: HttpException) {
            when (t.code()) {
                403 -> _userMessage.postEvent("Incorrect pin submitted. Try again")
                else -> _userMessage.postEvent("Error submitting pin (${t.code()}). Try again")
            }
            _pinLoadingStatus.postValue(LoadingStatus.ERROR)
        } catch (t: Throwable) {
            _userMessage.postEvent("Error occurred when submitting pin. Try again")
            Timber.e("Failed to submit pin: $t")
            _pinLoadingStatus.postValue(LoadingStatus.ERROR)
        }
    }

    fun setPinData(s: CharSequence) {
        if (s.length != 4) {
            _pinErrorMessage.postValue("Too short")
        } else {
            _pinErrorMessage.postValue("")
        }
        try {
            _pinData.value = s.toString()
        } catch (t: NumberFormatException) {
            _pinErrorMessage.postValue("Pin must be 0000-9999")
            Timber.e("Failed to parse pin to int!")
        }
    }

    fun hidePinScreen() {
        _showPin.postValue(false)
    }
}