package com.ryosoftware.contact_dates_notifier.ui.screen

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ryosoftware.contact_dates_notifier.R
import com.ryosoftware.contact_dates_notifier.data.ApplicationContactsDriver
import com.ryosoftware.contact_dates_notifier.data.ApplicationPreferences
import com.ryosoftware.contact_dates_notifier.data.DeviceContactsDriver
import com.ryosoftware.contact_dates_notifier.data.model.ApplicationContact
import com.ryosoftware.contact_dates_notifier.data.model.Contact
import com.ryosoftware.contact_dates_notifier.ui.theme.LocalPureBlackBackground
import com.ryosoftware.utilities.DateTimeUtilities

private data class DisabledEventInfo(
    val contact: Contact,
    val eventKey: String,
    val day: Int,
    val month: Int,
    val year: Int,
    val description: String
)

private data class ParsedKey(
    val day: Int,
    val month: Int,
    val year: Int,
    val description: String
)

private fun parseEventKey(eventKey: String): ParsedKey {
    val colonIndex = eventKey.indexOf(':')
    val datePart = eventKey.substring(0, colonIndex)
    val description = eventKey.substring(colonIndex + 1)
    val dateParts = datePart.split("-")
    return ParsedKey(dateParts[0].toInt(), dateParts[1].toInt(), dateParts[2].toInt(), description)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageDisabledUsersContent(
    context: Context,
    onBack: () -> Unit,
    onRemoveContact: (Contact) -> Unit,
    onRemoveEvent: (Contact, String) -> Unit
) {
    var disabledContacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var disabledEvents by remember { mutableStateOf<List<DisabledEventInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val allDeviceContacts = DeviceContactsDriver.get(context)
        val allAppContacts = ApplicationContactsDriver.get(context)
        val disabledContactsResult = allDeviceContacts.filter { contact ->
            ApplicationPreferences.Contacts.isDontDisplayAlertsFromThisContactEnabled(context, contact)
        } + allAppContacts.filter { contact ->
            contact is ApplicationContact && contact.disabled
        }
        disabledContacts = disabledContactsResult.sortedBy { it.name?.lowercase() }

        val allContacts = allDeviceContacts + allAppContacts
        val events = mutableListOf<DisabledEventInfo>()
        for (contact in allContacts) {
            val eventKeys = ApplicationPreferences.Contacts.getDisabledEvents(context, contact)
            for (eventKey in eventKeys) {
                val parsed = parseEventKey(eventKey)
                events.add(DisabledEventInfo(contact, eventKey, parsed.day, parsed.month, parsed.year, parsed.description))
            }
        }
        disabledEvents = events.sortedBy { "${it.contact.name?.lowercase()}" }
        loading = false
    }

    val hasItems = disabledContacts.isNotEmpty() || disabledEvents.isNotEmpty()

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    Scaffold(
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(context.getString(R.string.manage_disabled_users)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = context.getString(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else if (!hasItems) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = context.getString(R.string.no_disabled_contacts),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(disabledContacts, key = { "contact_${System.identityHashCode(it)}" }) { contact ->
                        DisabledContactCard(
                            contact = contact,
                            onRemove = {
                                onRemoveContact(contact)
                                disabledContacts = disabledContacts.filter { it != contact }
                            }
                        )
                    }
                    items(disabledEvents, key = { "event_${System.identityHashCode(it)}" }) { info ->
                        DisabledEventCard(
                            contact = info.contact,
                            day = info.day,
                            month = info.month,
                            year = info.year,
                            description = info.description,
                            onRemove = {
                                onRemoveEvent(info.contact, info.eventKey)
                                disabledEvents = disabledEvents.filter { it != info }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DisabledContactCard(
    contact: Contact,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val isPureBlack = LocalPureBlackBackground.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = if (isPureBlack) BorderStroke(1.dp, Color(0xFF444444)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ContactPhotoView(contact = contact, size = 40.dp)
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name ?: "",
                    style = MaterialTheme.typography.bodyLarge
                )
                if (contact.accountName != null) {
                    Text(
                        text = contact.accountName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.delete)
                )
            }
        }
    }
}

@Composable
private fun ContactPhotoView(
    contact: Contact,
    size: Dp = 40.dp
) {
    val context = LocalContext.current
    val photoUri = remember(contact) { contact.getPhotoUri(context) }

    if (photoUri != null) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(photoUri)
                .crossfade(true)
                .size(80)
                .build(),
            contentDescription = contact.name,
            modifier = Modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = (contact.name?.firstOrNull()?.uppercase() ?: stringResource(R.string.no_contact_photo_letter)),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DisabledEventCard(
    contact: Contact,
    day: Int,
    month: Int,
    year: Int,
    description: String,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val formatStyle = remember(context) { ApplicationPreferences.getDateFormatStyle(context) }
    val dateStr = remember(year, month, day, formatStyle) {
        if (year != null && year != Contact.Event.LEAP_YEAR)
            DateTimeUtilities.getDateString(formatStyle, year, month, day)
        else
            DateTimeUtilities.getDateStringWithoutYear(formatStyle, year, month, day)
    }
    val isPureBlack = LocalPureBlackBackground.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = if (isPureBlack) BorderStroke(1.dp, Color(0xFF444444)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ContactPhotoView(contact = contact, size = 40.dp)
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name ?: "",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(R.string.date_value_and_event_description, dateStr, description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.delete)
                )
            }
        }
    }
}
