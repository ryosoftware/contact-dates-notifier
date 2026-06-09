package com.ryosoftware.contact_dates_notifier.ui.screen

import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Gray
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ryosoftware.contact_dates_notifier.data.ApplicationPreferences
import com.ryosoftware.contact_dates_notifier.data.model.ApplicationContact
import com.ryosoftware.contact_dates_notifier.data.model.Contact
import com.ryosoftware.contact_dates_notifier.data.model.DeviceContact
import com.ryosoftware.contact_dates_notifier.ui.viewmodel.MainViewModel
import com.ryosoftware.utilities.DateTimeUtilities
import com.ryosoftware.contact_dates_notifier.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onAddContact: () -> Unit,
    onContactClick: (Contact) -> Unit,
    onSettingsClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeRefreshKey by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadContacts()
                resumeRefreshKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val disabledBehavior = remember(resumeRefreshKey) { ApplicationPreferences.getString(context, ApplicationPreferences.DISABLED_BEHAVIOR_KEY, ApplicationPreferences.DISABLED_BEHAVIOR_DEFAULT) ?: ApplicationPreferences.DISABLED_BEHAVIOR_DEFAULT }
    val hideDisabled = remember(disabledBehavior) { disabledBehavior == ApplicationPreferences.DISABLED_BEHAVIOR_HIDE }
    val grayDisabled = remember(disabledBehavior) { disabledBehavior == ApplicationPreferences.DISABLED_BEHAVIOR_GRAY }
    val groupContactEvents = remember(resumeRefreshKey) { ApplicationPreferences.getBoolean(context, ApplicationPreferences.GROUP_CONTACT_EVENTS_KEY, ApplicationPreferences.GROUP_CONTACT_EVENTS_DEFAULT) }

    var filteredContacts by remember(state.contacts, state.selectedAccount, searchQuery, hideDisabled) {
        mutableStateOf(emptyList<Contact>())
    }
    LaunchedEffect(state.contacts, state.selectedAccount, searchQuery, hideDisabled) {
        filteredContacts = withContext(Dispatchers.Default) {
            var list = when (state.selectedAccount) {
                null -> state.contacts
                ApplicationPreferences.APP_CONTACTS_ACCOUNT_NAME -> state.contacts.filterIsInstance<ApplicationContact>()
                ApplicationPreferences.LOCAL_CONTACTS_ACCOUNT_NAME -> state.contacts.filter { it is DeviceContact && it.accountName == null }
                else -> state.contacts.filter { it.accountName == state.selectedAccount }
            }
            if (hideDisabled) {
                list = list.filter { !isContactDisabled(it, context) }
            }
            if (searchQuery.isNotBlank()) {
                list = list.filter { it.name?.contains(searchQuery, ignoreCase = true) == true }
            }
            list
        }
    }

    val addHeaderTodayTomorrow = remember(resumeRefreshKey) { ApplicationPreferences.getBoolean(context, ApplicationPreferences.ADD_HEADER_FOR_YESTERDAY_TODAY_AND_TOMORROW_EVENTS_KEY, ApplicationPreferences.ADD_HEADER_FOR_YESTERDAY_TODAY_AND_TOMORROW_EVENTS_DEFAULT) }
    val useSystemAccent = remember(resumeRefreshKey) { ApplicationPreferences.getBoolean(context, ApplicationPreferences.USE_SYSTEM_ACCENT_KEY, ApplicationPreferences.USE_SYSTEM_ACCENT_DEFAULT) }
    val accentColor = if (useSystemAccent) MaterialTheme.colorScheme.primary else colorResource(R.color.primary)

    var groupedContacts by remember { mutableStateOf(emptyList<ContactGroup>()) }
    LaunchedEffect(filteredContacts, groupContactEvents, hideDisabled, addHeaderTodayTomorrow) {
        groupedContacts = withContext(Dispatchers.Default) {
            groupContactsByDate(filteredContacts, context, groupContactEvents, hideDisabled, addHeaderTodayTomorrow)
        }
    }

    var showCelebration by remember { mutableStateOf(false) }
    LaunchedEffect(state.contacts) {
        if (state.contacts.isNotEmpty() && !showCelebration) {
            val today = DateTimeUtilities.toMidnightTime(System.currentTimeMillis())
            val hasEventsToday = state.contacts.any { contact ->
                contact.events.any { event ->
                    DateTimeUtilities.refersToSameDay(event.nextIteration, today)
                }
            }
            if (hasEventsToday) {
                val lastDate = ApplicationPreferences.getLong(context, ApplicationPreferences.CELEBRATION_LAST_DATE_KEY, 0L)
                if (lastDate != today) {
                    ApplicationPreferences.putLong(context, ApplicationPreferences.CELEBRATION_LAST_DATE_KEY, today)
                    showCelebration = true
                }
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    var showAccountDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    if (isSearchActive) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(stringResource(R.string.search_contacts)) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = Color.White,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    } else {
                        val selectedAccountDisplay = when (state.selectedAccount) {
                            null -> stringResource(R.string.all_accounts)
                            ApplicationPreferences.APP_CONTACTS_ACCOUNT_NAME -> stringResource(R.string.app_contacts)
                            ApplicationPreferences.LOCAL_CONTACTS_ACCOUNT_NAME -> stringResource(R.string.local_contacts)
                            else -> state.selectedAccount ?: ""
                        }
                        FilterChip(
                            selected = state.selectedAccount != null,
                            onClick = { showAccountDialog = true },
                            label = { Text(selectedAccountDisplay, maxLines = 1, color = Color.White) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color.White.copy(alpha = 0.2f),
                                selectedLabelColor = Color.White,
                                containerColor = Color.Transparent,
                                labelColor = Color.White
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = false,
                                borderColor = Color.White,
                                selectedBorderColor = Color.White
                            )
                        )
                    }
                },
                navigationIcon = {
                    if (isSearchActive) {
                        IconButton(onClick = {
                            isSearchActive = false
                            searchQuery = ""
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close_search))
                        }
                    }
                },
                actions = {
                    if (!isSearchActive) {
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
                        }
                        IconButton(onClick = onAddContact) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_contact))
                        }
                    }
                    IconButton(onClick = { isSearchActive = !isSearchActive }) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = accentColor,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
                )
            }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).nestedScroll(scrollBehavior.nestedScrollConnection)) {
            when {
                state.isLoading && state.contacts.isEmpty() -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
                groupedContacts.isEmpty() -> EmptyState()
                else -> ContactList(
                    groupedContacts = groupedContacts,
                    onContactClick = onContactClick,
                    onToggleDisabled = { contact ->
                        toggleContactDisabled(contact, context)
                        if (hideDisabled) {
                            filteredContacts = filteredContacts - contact
                        } else {
                            groupedContacts = groupedContacts.map { group ->
                                group.copy(items = group.items.map { item ->
                                    if (item.contact == contact) item.copy() else item
                                })
                            }
                        }
                    },
                    onToggleEventDisabled = { contact, event ->
                        toggleEventDisabled(contact, event, context)
                        if (hideDisabled) {
                            groupedContacts = groupedContacts.mapNotNull { group ->
                                val filtered = group.items.filterNot { item ->
                                    item.contact == contact && item.event == event
                                }
                                if (filtered.isEmpty()) null else group.copy(items = filtered)
                            }
                        } else {
                            groupedContacts = groupedContacts.map { group ->
                                group.copy(items = group.items.map { item ->
                                    if (item.contact == contact && item.event == event) item.copy() else item
                                })
                            }
                        }
                    },
                    onNotesChanged = {
                        groupedContacts = groupedContacts.map { group ->
                            group.copy(items = group.items.map { it.copy() })
                        }
                    },
                    grayDisabled = grayDisabled,
                    groupEvents = groupContactEvents
                )
            }
        }
    }

    if (showAccountDialog) {
        AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            title = { Text(stringResource(R.string.accounts)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.selectAccount(null)
                                showAccountDialog = false
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = state.selectedAccount == null, onClick = {
                            viewModel.selectAccount(null)
                            showAccountDialog = false
                        })
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.all_accounts))
                    }
                    state.accounts.forEach { account ->
                        val displayName = when (account) {
                            ApplicationPreferences.APP_CONTACTS_ACCOUNT_NAME -> stringResource(R.string.app_contacts)
                            ApplicationPreferences.LOCAL_CONTACTS_ACCOUNT_NAME -> stringResource(R.string.local_contacts)
                            else -> account
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectAccount(account)
                                    showAccountDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = state.selectedAccount == account, onClick = {
                                viewModel.selectAccount(account)
                                showAccountDialog = false
                            })
                            Spacer(Modifier.width(8.dp))
                            Text(displayName)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAccountDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (showCelebration) {
        CelebrationOverlay(onDismiss = { showCelebration = false })
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.no_contacts_found),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun isContactDisabled(contact: Contact, context: android.content.Context): Boolean = when (contact) {
    is ApplicationContact -> contact.disabled
    else -> ApplicationPreferences.Contacts.isDontDisplayAlertsFromThisContactEnabled(context, contact)
}

private fun toggleContactDisabled(contact: Contact, context: android.content.Context) {
    when (contact) {
        is ApplicationContact -> {
            val newState = !contact.disabled
            contact.disabled = newState
            contact.setDisabled(context, newState)
        }
        is DeviceContact -> {
            val isDisabled = ApplicationPreferences.Contacts.isDontDisplayAlertsFromThisContactEnabled(context, contact)
            ApplicationPreferences.Contacts.setDontDisplayAlertsFromThisContact(context, contact, !isDisabled)
        }
    }
}

private fun toggleEventDisabled(contact: Contact, event: Contact.Event, context: android.content.Context) {
    val isDisabled = ApplicationPreferences.Contacts.isEventDisabled(context, contact, event)
    ApplicationPreferences.Contacts.setEventDisabled(context, contact, event, !isDisabled)
}

private fun isEventDisabled(contact: Contact, event: Contact.Event, context: android.content.Context): Boolean =
    ApplicationPreferences.Contacts.isEventDisabled(context, contact, event)

data class ContactDisplayItem(
    val contact: Contact,
    val event: Contact.Event? = null
)

data class ContactGroup(
    val label: String,
    val items: List<ContactDisplayItem>
)

@Composable
private fun ContactList(
    groupedContacts: List<ContactGroup>,
    onContactClick: (Contact) -> Unit,
    onToggleDisabled: (Contact) -> Unit,
    onToggleEventDisabled: (Contact, Contact.Event) -> Unit,
    onNotesChanged: () -> Unit,
    grayDisabled: Boolean,
    groupEvents: Boolean
) {
    val listState = rememberLazyListState()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        groupedContacts.forEach { group ->
            item(key = "header_${group.label}") {
                SectionHeader(label = group.label)
            }
            items(group.items, key = {
                val c = it.contact
                when {
                    c is DeviceContact -> "dev_${c.immutableContactIdentifier ?: c.databaseContactIdentifier}_${it.event?.time ?: "noev"}"
                    c is ApplicationContact -> "app_${c.id}_${it.event?.time ?: "noev"}"
                    else -> "other_${c.name}_${it.event?.time ?: "noev"}"
                }
            }) { item ->
                ContactItem(
                    contact = item.contact,
                    event = item.event,
                    onClick = { onContactClick(item.contact) },
                    onToggleDisabled = onToggleDisabled,
                    onToggleEventDisabled = onToggleEventDisabled,
                    onNotesChanged = onNotesChanged,
                    grayDisabled = grayDisabled,
                    groupEvents = groupEvents
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@SuppressLint("LocalContextResourcesRead")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ContactItem(
    contact: Contact,
    event: Contact.Event?,
    onClick: () -> Unit,
    onToggleDisabled: (Contact) -> Unit,
    onToggleEventDisabled: (Contact, Contact.Event) -> Unit,
    onNotesChanged: () -> Unit,
    grayDisabled: Boolean,
    groupEvents: Boolean
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var isEditingNotes by remember { mutableStateOf(false) }
    var notesText by remember { mutableStateOf(contact.notes ?: "") }
    val isDisabled = isContactDisabled(contact, context)
    val isCurrentEventDisabled = event != null && isEventDisabled(contact, event, context)
    val isEffectivelyDisabled = isDisabled || isCurrentEventDisabled
    val useSystemAccent = remember { ApplicationPreferences.getBoolean(context, ApplicationPreferences.USE_SYSTEM_ACCENT_KEY, ApplicationPreferences.USE_SYSTEM_ACCENT_DEFAULT) }
    val badgeColor = if (isEffectivelyDisabled && grayDisabled) Gray else if (useSystemAccent) MaterialTheme.colorScheme.primary else colorResource(R.color.primary)

    val showLargeNear = remember { ApplicationPreferences.getBoolean(context, ApplicationPreferences.SHOW_LARGE_PHOTO_FOR_CONTACTS_THAT_HAS_EVENTS_IN_THE_NEAR_FUTURE_KEY, ApplicationPreferences.SHOW_LARGE_PHOTO_FOR_CONTACTS_THAT_HAS_EVENTS_IN_THE_NEAR_FUTURE_DEFAULT) }
    val showLargeNoNear = remember { ApplicationPreferences.getBoolean(context, ApplicationPreferences.SHOW_LARGE_PHOTO_FOR_CONTACTS_THAT_DO_NOT_HAS_EVENTS_IN_THE_NEAR_FUTURE_KEY, ApplicationPreferences.SHOW_LARGE_PHOTO_FOR_CONTACTS_THAT_DO_NOT_HAS_EVENTS_IN_THE_NEAR_FUTURE_DEFAULT) }
    val dateFormatStyle = remember(context) { ApplicationPreferences.getDateFormatStyle(context) }
    val eventForDays = event ?: contact.firstEvent
    val days = remember(eventForDays) { eventForDays?.daysUntilNextIteration() ?: Int.MAX_VALUE }
    val nearFutureDays = remember { ApplicationPreferences.getInteger(context, ApplicationPreferences.NOTIFICATION_DAYS_LEFT_KEY, ApplicationPreferences.NOTIFICATION_DAYS_LEFT_DEFAULT) }
    val isNearFuture = days in 0..nearFutureDays
    val showLargePhoto = if (isNearFuture) showLargeNear else showLargeNoNear

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .then(if (isEffectivelyDisabled && grayDisabled) Modifier.alpha(0.5f) else Modifier)
                .combinedClickable(
                    onClick = { showMenu = true },
                    onLongClick = onClick
                ),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column {
                if (showLargePhoto) {
                    ContactPhoto(
                        contact = contact,
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        grayDisabled = isEffectivelyDisabled && grayDisabled,
                        large = true
                    )
                }
                Row(modifier = Modifier.padding(12.dp)) {
                    if (!showLargePhoto) {
                        ContactPhoto(
                            contact = contact,
                            modifier = Modifier.size(72.dp),
                            grayDisabled = isEffectivelyDisabled && grayDisabled
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = contact.name ?: stringResource(R.string.unknown),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    ContactEventsContent(contact = contact, singleEvent = event)
                    contact.groups?.takeIf { it.isNotEmpty() }?.let { groups ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            groups.forEach { group ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = group,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                    contact.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (isEffectivelyDisabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = badgeColor
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.NotificationsOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.badge_no_alerts),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }

        if (showMenu) {
            ModalBottomSheet(
                onDismissRequest = { showMenu = false; isEditingNotes = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ContactPhoto(
                        contact = contact,
                        modifier = Modifier.size(72.dp),
                        grayDisabled = isEffectivelyDisabled && grayDisabled
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = contact.name ?: stringResource(R.string.unknown),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val birthdayEvent = contact.birthEvent
                    if (birthdayEvent != null) {
                        val parts = DateTimeUtilities.getDateParts(birthdayEvent.time)
                        val zodiacResId = getZodiacResId(parts[2], parts[1])
                        val dateStr = DateTimeUtilities.getDateString(dateFormatStyle, birthdayEvent.time)
                        val ageStr = if (birthdayEvent.hasYear) context.resources.getQuantityString(R.plurals.age_years, birthdayEvent.yearsUntilNextIteration, birthdayEvent.yearsUntilNextIteration) else null
                        when {
                            ageStr != null && zodiacResId != null -> Text(
                                text = stringResource(R.string.zodiac_with_date_and_age, dateStr, ageStr, stringResource(zodiacResId)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                            ageStr != null -> Text(
                                text = stringResource(R.string.date_with_age, dateStr, ageStr),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                            zodiacResId != null -> Text(
                                text = stringResource(R.string.zodiac_with_date, dateStr, stringResource(zodiacResId)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    val accountDisplay = when {
                        (contact is ApplicationContact) -> stringResource(R.string.contact_stored_at, stringResource(R.string.app_contacts))
                        (contact.accountName == null) || (contact.accountName == ApplicationPreferences.LOCAL_CONTACTS_ACCOUNT_NAME) -> stringResource(R.string.contact_stored_at, stringResource(R.string.local_contacts))
                        else -> stringResource(R.string.contact_stored_at, contact.accountName ?: "")
                    }
                    Text(
                        text = accountDisplay,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isEditingNotes) {
                            notesText = contact.notes ?: ""
                            isEditingNotes = true
                        }
                        .padding(horizontal = 24.dp)
                        .padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.contact_notes)
                    )
                    if (isEditingNotes) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.cancel),
                                modifier = Modifier.size(20.dp).clickable {
                                    isEditingNotes = false
                                },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = stringResource(R.string.save),
                                modifier = Modifier.size(20.dp).clickable {
                                    contact.setNotes(context, notesText)
                                    isEditingNotes = false
                                    onNotesChanged()
                                },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.edit_notes),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (isEditingNotes) {
                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        label = { Text(stringResource(R.string.contact_notes)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        minLines = 2
                    )
                } else if (!contact.notes.isNullOrEmpty()) {
                    Text(
                        text = contact.notes ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                notesText = contact.notes ?: ""
                                isEditingNotes = true
                            }
                            .padding(horizontal = 24.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showMenu = false
                            onClick()
                        }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.view_or_edit_contact_data),
                        modifier = Modifier.weight(1f)
                    )
                    if (contact !is ApplicationContact) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showMenu = false
                            onToggleDisabled(contact)
                        }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.show_alerts_for_contact)
                    )
                    Switch(
                        checked = !isDisabled,
                        onCheckedChange = null
                    )
                }
                val sortedEvents = remember(contact) { contact.events.sortedBy { it.nextIteration } }
                if (sortedEvents.size > 1) {
                    sortedEvents.forEach { ev ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isDisabled) {
                                    showMenu = false
                                    onToggleEventDisabled(contact, ev)
                                }
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                modifier = Modifier.weight(1f),
                                text = stringResource(
                                    R.string.show_alerts_for_event_with_date,
                                    ev.description,
                                    DateTimeUtilities.getDateString(dateFormatStyle, ev.time)
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Switch(
                                checked = !isDisabled && !isEventDisabled(contact, ev, context),
                                onCheckedChange = null,
                                enabled = !isDisabled
                            )
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun ContactEventsContent(contact: Contact, singleEvent: Contact.Event?) {
    val context = LocalContext.current
    val formatStyle = remember(context) { ApplicationPreferences.getDateFormatStyle(context) }
    val events = remember(contact, singleEvent) {
        if (singleEvent != null) listOf(singleEvent)
        else contact.events.sortedBy { event ->
            val days = DateTimeUtilities.getRelativeDays(event.time)
            if (days >= -1) days + 1 else Int.MAX_VALUE
        }
    }

    Column {
        events.forEach { event ->
            val relativeDays = remember(event) { DateTimeUtilities.getRelativeDays(event.time) }
            val daysRemaining = remember(event, relativeDays) {
                if (relativeDays == -1) -1 else event.daysUntilNextIteration()
            }
            val dateStr = remember(event, formatStyle, relativeDays) {
                if (relativeDays == -1) {
                    val cal = DateTimeUtilities.getCalendar(event.time)
                    cal.set(Calendar.YEAR, Calendar.getInstance().get(Calendar.YEAR))
                    DateTimeUtilities.getDateString(formatStyle, cal.timeInMillis)
                } else {
                    DateTimeUtilities.getDateString(formatStyle, event.nextIteration)
                }
            }
            val repetitionNumber = remember(event) { event.yearsUntilNextIteration }
            val displayText = remember(daysRemaining, dateStr, event, context) {
                if (event.hasYear) {
                    when (daysRemaining) {
                        0 -> context.getString(R.string.date_value_and_event_description_and_zero_days_remaining, dateStr, event.description, repetitionNumber)
                        -1 -> context.getString(R.string.date_value_and_event_description_and_minus_one_days_remaining, dateStr, event.description, repetitionNumber)
                        else -> context.resources.getQuantityString(R.plurals.date_value_and_event_description_and_days_remaining, daysRemaining, dateStr, event.description, daysRemaining, repetitionNumber)
                    }
                } else {
                    when (daysRemaining) {
                        0 -> context.getString(R.string.date_value_and_event_description_and_zero_days_remaining_no_year, dateStr, event.description)
                        -1 -> context.getString(R.string.date_value_and_event_description_and_minus_one_days_remaining_no_year, dateStr, event.description)
                        else -> context.resources.getQuantityString(R.plurals.date_value_and_event_description_and_days_remaining_no_year, daysRemaining, dateStr, event.description, daysRemaining)
                    }
                }
            }
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ContactPhoto(
    contact: Contact,
    modifier: Modifier = Modifier,
    grayDisabled: Boolean = false,
    large: Boolean = false
) {
    val context = LocalContext.current
    val photoUri = remember(contact) { contact.getPhotoUri(context) }

    if (photoUri != null) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(photoUri)
                .crossfade(true)
                .size(if (large) 400 else 144)
                .build(),
            contentDescription = contact.name,
            modifier = if (large) modifier else modifier.clip(CircleShape),
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            colorFilter = if (grayDisabled) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null
        )
    } else {
        PhotoPlaceholder(contact = contact, modifier = modifier, large = large)
    }
}

@Composable
private fun PhotoPlaceholder(
    contact: Contact,
    modifier: Modifier = Modifier,
    large: Boolean = false
) {
    Box(
        modifier = modifier
            .then(if (large) Modifier else Modifier.clip(CircleShape))
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

private sealed class HeaderCategory : Comparable<HeaderCategory> {
    data object Yesterday : HeaderCategory()
    data object Today : HeaderCategory()
    data object Tomorrow : HeaderCategory()
    data object ThisWeek : HeaderCategory()
    data object FollowingWeek : HeaderCategory()
    data object ThisMonth : HeaderCategory()
    data class MonthHeader(val year: Int, val month: Int) : HeaderCategory()
    data object NoEvents : HeaderCategory()

    private val order: Int get() = when (this) {
        Yesterday -> -1
        Today -> 0
        Tomorrow -> 1
        ThisWeek -> 2
        FollowingWeek -> 3
        ThisMonth -> 4
        is MonthHeader -> 5
        NoEvents -> 6
    }

    override fun compareTo(other: HeaderCategory): Int {
        val cmp = order.compareTo(other.order)
        if (cmp != 0) return cmp
        if (this is MonthHeader && other is MonthHeader) {
            val y = year.compareTo(other.year)
            return if (y != 0) y else month.compareTo(other.month)
        }
        return 0
    }
}

private fun headerCategoryForEvent(event: Contact.Event, today: Calendar, collapseTodayTomorrow: Boolean = false): HeaderCategory {
    val days = DateTimeUtilities.getRelativeDays(event.time)
    val firstDayOfWeek = today.firstDayOfWeek
    val lastDayOfWeek = if (firstDayOfWeek == Calendar.SUNDAY) Calendar.SATURDAY else Calendar.SUNDAY
    val currentDayOfWeek = today.get(Calendar.DAY_OF_WEEK)
    val daysUntilEndOfWeek = (lastDayOfWeek - currentDayOfWeek + 7) % 7
    val daysSinceStartOfWeek = (currentDayOfWeek - firstDayOfWeek + 7) % 7
    when {
        days == -1 && !collapseTodayTomorrow -> return HeaderCategory.Yesterday
        days == -1 && collapseTodayTomorrow && daysSinceStartOfWeek >= 1 -> return HeaderCategory.ThisWeek
        days == 0 -> return if (collapseTodayTomorrow) HeaderCategory.ThisWeek else HeaderCategory.Today
        days == 1 && !collapseTodayTomorrow -> return HeaderCategory.Tomorrow
        days in 1..daysUntilEndOfWeek -> return HeaderCategory.ThisWeek
        days in (daysUntilEndOfWeek + 1)..(daysUntilEndOfWeek + 7) -> return HeaderCategory.FollowingWeek
    }
    val eventCal = Calendar.getInstance()
    eventCal.timeInMillis = event.nextIteration
    val eventYear = eventCal.get(Calendar.YEAR)
    val eventMonth = eventCal.get(Calendar.MONTH)
    if (eventYear == today.get(Calendar.YEAR) && eventMonth == today.get(Calendar.MONTH)) {
        return HeaderCategory.ThisMonth
    }
    return HeaderCategory.MonthHeader(eventYear, eventMonth)
}

private fun formatMonthHeader(year: Int, month: Int, context: android.content.Context): String {
    val today = Calendar.getInstance()
    val cal = Calendar.getInstance()
    cal.set(Calendar.YEAR, year)
    cal.set(Calendar.MONTH, month)
    cal.set(Calendar.DAY_OF_MONTH, 1)
    val format = SimpleDateFormat("MMMM", Locale.getDefault())
    val monthName = format.format(Date(cal.timeInMillis))
    return if (year == today.get(Calendar.YEAR)) context.getString(R.string.month_this_year_header, year, monthName)
    else context.getString(R.string.month_next_year_header, year, monthName)
}

private fun groupContactsByDate(contacts: List<Contact>, context: android.content.Context, groupEvents: Boolean, hideDisabled: Boolean, addHeaderTodayTomorrow: Boolean = true): List<ContactGroup> {
    if (contacts.isEmpty()) return emptyList()

    val today = Calendar.getInstance()
    val groups = mutableMapOf<HeaderCategory, MutableList<ContactDisplayItem>>()
    val noEventItems = mutableListOf<ContactDisplayItem>()
    val collapseTT = !addHeaderTodayTomorrow

    if (groupEvents) {
        for (contact in contacts) {
            if (contact.events.isEmpty()) {
                noEventItems.add(ContactDisplayItem(contact))
                continue
            }
            val bestEvent = contact.events.minByOrNull { event ->
                val days = DateTimeUtilities.getRelativeDays(event.time)
                if (days >= -1) days + 1 else Int.MAX_VALUE
            }!!
            val header = headerCategoryForEvent(bestEvent, today, collapseTT)
            groups.getOrPut(header) { mutableListOf() }.add(ContactDisplayItem(contact))
        }
    } else {
        for (contact in contacts) {
            if (contact.events.isEmpty()) {
                noEventItems.add(ContactDisplayItem(contact))
                continue
            }
            for (event in contact.events) {
                val eventDisabled = isEventDisabled(contact, event, context)
                if (hideDisabled && eventDisabled) continue
                val header = headerCategoryForEvent(event, today, collapseTT)
                groups.getOrPut(header) { mutableListOf() }.add(ContactDisplayItem(contact, event))
            }
        }
    }

    val result = mutableListOf<ContactGroup>()
    val specialOrder = listOf(
        HeaderCategory.Yesterday to context.getString(R.string.yesterday_header),
        HeaderCategory.Today to context.getString(R.string.today_header),
        HeaderCategory.Tomorrow to context.getString(R.string.tomorrow_header),
        HeaderCategory.ThisWeek to context.getString(R.string.this_week_header),
        HeaderCategory.FollowingWeek to context.getString(R.string.next_week_header),
        HeaderCategory.ThisMonth to context.getString(R.string.this_month_header)
    )
    for ((cat, label) in specialOrder) {
        groups.remove(cat)?.let { result.add(ContactGroup(label, it)) }
    }
    for ((cat, items) in groups.toSortedMap().filterKeys { it is HeaderCategory.MonthHeader }) {
        val month = cat as HeaderCategory.MonthHeader
        result.add(ContactGroup(formatMonthHeader(month.year, month.month, context), items))
    }
    if (noEventItems.isNotEmpty()) result.add(ContactGroup(context.getString(R.string.without_events), noEventItems))
    return result
}

private fun getZodiacResId(day: Int, month: Int): Int? {
    return when {
        (month == 3 && day >= 21) || (month == 4 && day <= 19) -> R.string.zodiac_aries
        (month == 4) || (month == 5 && day <= 20) -> R.string.zodiac_taurus
        (month == 5) || (month == 6 && day <= 20) -> R.string.zodiac_gemini
        (month == 6) || (month == 7 && day <= 22) -> R.string.zodiac_cancer
        (month == 7) || (month == 8 && day <= 22) -> R.string.zodiac_leo
        (month == 8) || (month == 9 && day <= 22) -> R.string.zodiac_virgo
        (month == 9) || (month == 10 && day <= 22) -> R.string.zodiac_libra
        (month == 10) || (month == 11 && day <= 21) -> R.string.zodiac_scorpio
        (month == 11) || (month == 12 && day <= 21) -> R.string.zodiac_sagittarius
        (month == 12) || (month == 1 && day <= 19) -> R.string.zodiac_capricorn
        (month == 1) || (month == 2 && day <= 18) -> R.string.zodiac_aquarius
        (month == 2) || (month == 3) -> R.string.zodiac_pisces
        else -> null
    }
}


