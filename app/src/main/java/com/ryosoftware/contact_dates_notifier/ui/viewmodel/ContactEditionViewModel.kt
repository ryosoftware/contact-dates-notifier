package com.ryosoftware.contact_dates_notifier.ui.viewmodel

import android.app.Application
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ryosoftware.contact_dates_notifier.data.model.ApplicationContact
import com.ryosoftware.contact_dates_notifier.data.model.ApplicationContactEvent
import com.ryosoftware.contact_dates_notifier.data.repository.ContactsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ContactEditionViewModel(application: Application) : AndroidViewModel(application) {

    private val contactsRepository = ContactsRepository(application)

    data class UiState(
        val contact: ApplicationContact? = null,
        val name: String = "",
        val events: List<ApplicationContactEvent> = emptyList(),
        val photo: Drawable? = null,
        val isLoading: Boolean = false,
        val isSaving: Boolean = false,
        val error: String? = null,
        val savedSuccessfully: Boolean = false,
        val isDirty: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var initialName: String = ""
    private var initialEventsCount: Int = 0

    fun loadContact(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val contact = contactsRepository.getAppContact(id)
                val name = contact?.name ?: ""
                val events = contact?.events?.map { event ->
                    val appEvent = event as ApplicationContactEvent
                    ApplicationContactEvent(
                        description = appEvent.description,
                        day = appEvent.day,
                        month = appEvent.month,
                        year = appEvent.year,
                        disabled = appEvent.disabled
                    ).also {
                        it.descriptionChanged = false
                        it.timeChanged = false
                    }
                } ?: emptyList()
                initialName = name
                initialEventsCount = events.size
                _uiState.update {
                    it.copy(
                        contact = contact,
                        name = name,
                        events = events,
                        photo = contact?.getPhoto(getApplication()),
                        isLoading = false,
                        isDirty = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun createContact() {
        initialName = ""
        initialEventsCount = 0
        _uiState.update {
            it.copy(
                contact = ApplicationContact(name = ""),
                name = "",
                events = emptyList(),
                photo = null,
                isDirty = false
            )
        }
    }

    fun updateName(name: String) {
        _uiState.update { it.copy(name = name, isDirty = name != initialName || it.events.size != initialEventsCount) }
    }

    fun setPhoto(photo: Drawable?) {
        _uiState.update { it.copy(photo = photo) }
    }

    fun setNewPhoto(file: File?) {
        _uiState.value.contact?.newPhoto = file
    }

    fun addEvent(description: String, day: Int, month: Int, year: Int) {
        _uiState.update {
            val newEvent = ApplicationContactEvent(
                description = description,
                day = day,
                month = month,
                year = year
            )
            val newEvents = it.events + newEvent
            it.copy(events = newEvents, isDirty = it.name != initialName || newEvents.size != initialEventsCount)
        }
    }

    fun removeEvent(event: ApplicationContactEvent) {
        _uiState.update {
            val newEvents = it.events.filter { e -> e !== event }
            it.copy(events = newEvents, isDirty = it.name != initialName || newEvents.size != initialEventsCount)
        }
    }

    fun save() {
        val contact = _uiState.value.contact ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                withContext(Dispatchers.IO) {
                    contact.name = _uiState.value.name
                    contact.events.clear()
                    for (event in _uiState.value.events) {
                        contact.addAppEvent(
                            event.description,
                            event.day,
                            event.month,
                            event.year,
                            event.disabled
                        )
                    }
                    contact.update(getApplication())
                }
                _uiState.update { it.copy(isSaving = false, savedSuccessfully = true, isDirty = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun delete() {
        val contact = _uiState.value.contact ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                withContext(Dispatchers.IO) { contact.delete(getApplication()) }
                _uiState.update { it.copy(isSaving = false, savedSuccessfully = true, isDirty = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }
}
