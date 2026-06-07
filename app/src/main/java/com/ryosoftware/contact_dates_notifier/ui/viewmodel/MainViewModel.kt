package com.ryosoftware.contact_dates_notifier.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ryosoftware.contact_dates_notifier.data.ApplicationPreferences
import com.ryosoftware.contact_dates_notifier.data.DeviceContactsDriver
import com.ryosoftware.contact_dates_notifier.data.model.ApplicationContact
import com.ryosoftware.contact_dates_notifier.data.model.Contact
import com.ryosoftware.contact_dates_notifier.data.model.DeviceContact
import com.ryosoftware.contact_dates_notifier.data.repository.ContactsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val contactsRepository = ContactsRepository(application)

    data class UiState(
        val contacts: List<Contact> = emptyList(),
        val accounts: List<String> = emptyList(),
        val selectedAccount: String? = null,
        val isLoading: Boolean = false,

        val error: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadContacts()
    }

    fun loadContacts() {
        DeviceContactsDriver.invalidateCache()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val contacts = contactsRepository.getAllContacts()
                val accounts = buildList {
                    addAll(contactsRepository.getAccounts())
                    if (contacts.any { it is DeviceContact && it.accountName == null }) {
                        add(ApplicationPreferences.LOCAL_CONTACTS_ACCOUNT_NAME)
                    }
                    if (contacts.any { it is ApplicationContact }) {
                        add(ApplicationPreferences.APP_CONTACTS_ACCOUNT_NAME)
                    }
                }
                _uiState.update {
                    it.copy(
                        contacts = contacts,
                        accounts = accounts,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message)
                }
            }
        }
    }



    fun selectAccount(account: String?) {
        _uiState.update { it.copy(selectedAccount = account) }
    }

    val filteredContacts: List<Contact>
        get() {
            val state = _uiState.value
            return if (state.selectedAccount == null) state.contacts
            else state.contacts.filter { it.accountName == state.selectedAccount }
        }
}
