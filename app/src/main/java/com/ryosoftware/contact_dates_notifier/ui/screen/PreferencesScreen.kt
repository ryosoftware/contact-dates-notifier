package com.ryosoftware.contact_dates_notifier.ui.screen

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.work.WorkManager
import com.ryosoftware.contact_dates_notifier.data.ApplicationPreferences
import com.ryosoftware.contact_dates_notifier.data.DeviceContactsDriver
import com.ryosoftware.contact_dates_notifier.data.ApplicationContactsDriver
import com.ryosoftware.contact_dates_notifier.MainService
import com.ryosoftware.contact_dates_notifier.ui.activities.ManageDisabledUsersActivity
import com.ryosoftware.contact_dates_notifier.R
import com.ryosoftware.utilities.DateTimeUtilities
import com.ryosoftware.contact_dates_notifier.data.model.ApplicationContact
import androidx.activity.compose.rememberLauncherForActivityResult
import com.ryosoftware.contact_dates_notifier.BuildConfig
import com.ryosoftware.utilities.StringUtilities
import java.time.format.FormatStyle
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val NOTIFICATION_DAYS_LEFT_MIN = 0
private const val NOTIFICATION_DAYS_LEFT_MAX = 30

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesContent(
    context: Context,
    onThemeChanged: () -> Unit = {}
) {
    var enableNotifications by remember { mutableStateOf(ApplicationPreferences.getBoolean(context, ApplicationPreferences.ENABLE_NOTIFICATIONS_KEY, ApplicationPreferences.ENABLE_NOTIFICATIONS_DEFAULT)) }
    var clearableNotification by remember { mutableStateOf(ApplicationPreferences.getBoolean(context, ApplicationPreferences.CLEARABLE_NOTIFICATION_KEY, ApplicationPreferences.CLEARABLE_NOTIFICATION_DEFAULT)) }
    var groupContactEvents by remember { mutableStateOf(ApplicationPreferences.getBoolean(context, ApplicationPreferences.GROUP_CONTACT_EVENTS_KEY, ApplicationPreferences.GROUP_CONTACT_EVENTS_DEFAULT)) }
    var disabledBehavior by remember { mutableStateOf(ApplicationPreferences.getString(context, ApplicationPreferences.DISABLED_BEHAVIOR_KEY, ApplicationPreferences.DISABLED_BEHAVIOR_DEFAULT) ?: ApplicationPreferences.DISABLED_BEHAVIOR_DEFAULT) }
    var showLargePhotoNearFuture by remember { mutableStateOf(ApplicationPreferences.getBoolean(context, ApplicationPreferences.SHOW_LARGE_PHOTO_FOR_CONTACTS_THAT_HAS_EVENTS_IN_THE_NEAR_FUTURE_KEY, ApplicationPreferences.SHOW_LARGE_PHOTO_FOR_CONTACTS_THAT_HAS_EVENTS_IN_THE_NEAR_FUTURE_DEFAULT)) }
    var showLargePhotoNoNearFuture by remember { mutableStateOf(ApplicationPreferences.getBoolean(context, ApplicationPreferences.SHOW_LARGE_PHOTO_FOR_CONTACTS_THAT_DO_NOT_HAS_EVENTS_IN_THE_NEAR_FUTURE_KEY, ApplicationPreferences.SHOW_LARGE_PHOTO_FOR_CONTACTS_THAT_DO_NOT_HAS_EVENTS_IN_THE_NEAR_FUTURE_DEFAULT)) }
    var addHeaderTodayTomorrow by remember { mutableStateOf(ApplicationPreferences.getBoolean(context, ApplicationPreferences.ADD_HEADER_FOR_YESTERDAY_TODAY_AND_TOMORROW_EVENTS_KEY, ApplicationPreferences.ADD_HEADER_FOR_YESTERDAY_TODAY_AND_TOMORROW_EVENTS_DEFAULT)) }
    var themeMode by remember { mutableStateOf(ApplicationPreferences.getString(context, ApplicationPreferences.THEME_STYLE_KEY, ApplicationPreferences.THEME_STYLE_DEFAULT) ?: ApplicationPreferences.THEME_STYLE_DEFAULT) }
    var pureBlackBackground by remember { mutableStateOf(ApplicationPreferences.getBoolean(context, ApplicationPreferences.PURE_BLACK_BACKGROUND_KEY, ApplicationPreferences.PURE_BLACK_BACKGROUND_DEFAULT)) }
    var useSystemAccent by remember { mutableStateOf(ApplicationPreferences.getBoolean(context, ApplicationPreferences.USE_SYSTEM_ACCENT_KEY, ApplicationPreferences.USE_SYSTEM_ACCENT_DEFAULT)) }
    var dateFormat by remember { mutableStateOf(ApplicationPreferences.getString(context, ApplicationPreferences.DATE_FORMAT_KEY, ApplicationPreferences.DATE_FORMAT_DEFAULT) ?: ApplicationPreferences.DATE_FORMAT_DEFAULT) }
    var disabledContactsCount by remember { mutableIntStateOf(0) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        enableNotifications = ApplicationPreferences.getBoolean(context, ApplicationPreferences.ENABLE_NOTIFICATIONS_KEY, ApplicationPreferences.ENABLE_NOTIFICATIONS_DEFAULT)
        if (enableNotifications) MainService.schedule(context)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                coroutineScope.launch(Dispatchers.IO) {
                    val deviceContacts = DeviceContactsDriver.get(context)
                    val appContacts = ApplicationContactsDriver.get(context)
                    val deviceDisabled = deviceContacts.count { contact ->
                        ApplicationPreferences.Contacts.isDontDisplayAlertsFromThisContactEnabled(context, contact)
                    }
                    val appDisabled = appContacts.count { contact ->
                        contact is ApplicationContact && contact.disabled
                    }
                    val disabledEvents = (deviceContacts + appContacts).sumOf { contact ->
                        ApplicationPreferences.Contacts.getDisabledEvents(context, contact).size
                    }
                    disabledContactsCount = deviceDisabled + appDisabled + disabledEvents
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showNotNotifiedAccountsDialog by remember { mutableStateOf(false) }
    var notNotifiedAccountsRefreshKey by remember { mutableStateOf(0) }
    var showNotificationDaysLeftDialog by remember { mutableStateOf(false) }
    var showNotificationHourDialog by remember { mutableStateOf(false) }
    var showDisabledBehaviorDialog by remember { mutableStateOf(false) }
    var showDateFormatDialog by remember { mutableStateOf(false) }
    var showThemeModeDialog by remember { mutableStateOf(false) }

    var notNotifiedAccountsSummary by remember { mutableStateOf(context.getString(R.string.not_notified_accounts_none)) }
    LaunchedEffect(notNotifiedAccountsRefreshKey) {
        notNotifiedAccountsSummary = withContext(Dispatchers.Default) {
            val notNotifiedAccounts = ApplicationPreferences.getStrings(context, ApplicationPreferences.NOT_NOTIFIED_ACCOUNTS_KEY, ApplicationPreferences.NOT_NOTIFIED_ACCOUNTS_DEFAULT)
            if (!notNotifiedAccounts.isNullOrEmpty()) {
                val allAccounts = DeviceContactsDriver.getAccounts(context) +
                        ApplicationPreferences.LOCAL_CONTACTS_ACCOUNT_NAME +
                        ApplicationPreferences.APP_CONTACTS_ACCOUNT_NAME
                val exceptions = notNotifiedAccounts.filter { it in allAccounts }
                if (exceptions.isNotEmpty()) {
                    val names = exceptions.map {
                        when (it) {
                            ApplicationPreferences.APP_CONTACTS_ACCOUNT_NAME -> context.getString(R.string.app_contacts)
                            ApplicationPreferences.LOCAL_CONTACTS_ACCOUNT_NAME -> context.getString(R.string.local_contacts)
                            else -> it
                        }
                    }
                    context.getString(R.string.not_notified_accounts_selected, StringUtilities.join(names, context.getString(R.string.strings_middle_separator), context.getString(R.string.strings_last_separator)))
                } else context.getString(R.string.not_notified_accounts_none)
            } else context.getString(R.string.not_notified_accounts_none)
        }
    }

    val notificationHourSummary = remember { DateTimeUtilities.toPrintableTime(context, ApplicationPreferences.getString(context, ApplicationPreferences.NOTIFICATION_HOUR_KEY, ApplicationPreferences.NOTIFICATION_HOUR_DEFAULT)) }

    var notificationDaysLeftValue by remember { mutableStateOf(ApplicationPreferences.getInteger(context, ApplicationPreferences.NOTIFICATION_DAYS_LEFT_KEY, ApplicationPreferences.NOTIFICATION_DAYS_LEFT_DEFAULT)) }
    val daysLabel = remember(notificationDaysLeftValue) {
        if (notificationDaysLeftValue == 0) context.getString(R.string.days_0)
        else context.resources.getQuantityString(R.plurals.days, notificationDaysLeftValue, notificationDaysLeftValue)
    }
    val notificationDaysLeftSummary = remember(notificationDaysLeftValue) {
        if (notificationDaysLeftValue == 0) context.getString(R.string.notify_the_same_day)
        else context.getString(R.string.notification_days_left_summary, notificationDaysLeftValue)
    }
    val showLargeNearSummaryOn = remember(notificationDaysLeftValue, daysLabel) {
        context.getString(R.string.show_large_photo_for_contacts_that_has_events_in_the_near_future_on_summary, daysLabel)
    }
    val showLargeNearSummaryOff = remember(notificationDaysLeftValue, daysLabel) {
        context.getString(R.string.show_large_photo_for_contacts_that_has_events_in_the_near_future_off_summary, daysLabel)
    }
    val showLargeNoNearSummaryOn = remember(notificationDaysLeftValue, daysLabel) {
        context.getString(R.string.show_large_photo_for_contacts_that_do_not_has_events_in_the_near_future_on_summary, daysLabel)
    }
    val showLargeNoNearSummaryOff = remember(notificationDaysLeftValue, daysLabel) {
        context.getString(R.string.show_large_photo_for_contacts_that_do_not_has_events_in_the_near_future_off_summary, daysLabel)
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    Scaffold(
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(context.getString(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = { (context as? Activity)?.finish() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
        ) {
            SwitchPreference(
                title = context.getString(R.string.enable_notifications),
                summaryOn = context.getString(R.string.enable_notifications_on_summary),
                summaryOff = context.getString(R.string.enable_notifications_off_summary),
                checked = enableNotifications,
                onCheckedChange = { checked ->
                    ApplicationPreferences.putBoolean(context, ApplicationPreferences.ENABLE_NOTIFICATIONS_KEY, checked)
                    enableNotifications = checked
                    if (checked) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            MainService.schedule(context)
                        }
                    } else {
                        WorkManager.getInstance(context).cancelUniqueWork(MainService::class.java.name)
                    }
                }
            )

            ClickablePreference(
                title = context.getString(R.string.not_notified_accounts_title),
                summary = notNotifiedAccountsSummary,
                enabled = enableNotifications,
                onClick = { showNotNotifiedAccountsDialog = true }
            )

            ClickablePreference(
                title = context.getString(R.string.notification_days_left),
                summary = notificationDaysLeftSummary,
                enabled = enableNotifications,
                onClick = { showNotificationDaysLeftDialog = true }
            )

            ClickablePreference(
                title = context.getString(R.string.notification_hour),
                summary = notificationHourSummary,
                enabled = enableNotifications,
                onClick = { showNotificationHourDialog = true }
            )

            SwitchPreference(
                title = context.getString(R.string.clearable_notification),
                summaryOn = context.getString(R.string.clearable_notification_on_summary),
                summaryOff = context.getString(R.string.clearable_notification_off_summary),
                checked = clearableNotification,
                enabled = enableNotifications,
                onCheckedChange = { checked ->
                    ApplicationPreferences.putBoolean(context, ApplicationPreferences.CLEARABLE_NOTIFICATION_KEY, checked)
                    clearableNotification = checked
                }
            )

            SectionHeader(context.getString(R.string.contacts_list))

            SwitchPreference(
                title = context.getString(R.string.group_contact_events),
                summaryOn = context.getString(R.string.group_contact_events_on_summary),
                summaryOff = context.getString(R.string.group_contact_events_off_summary),
                checked = groupContactEvents,
                onCheckedChange = { checked ->
                    ApplicationPreferences.putBoolean(context, ApplicationPreferences.GROUP_CONTACT_EVENTS_KEY, checked)
                    groupContactEvents = checked
                }
            )

            ClickablePreference(
                title = context.getString(R.string.disabled_behavior),
                summary = when (disabledBehavior) {
                    ApplicationPreferences.DISABLED_BEHAVIOR_HIDE -> context.getString(R.string.disabled_behavior_hide_disabled)
                    ApplicationPreferences.DISABLED_BEHAVIOR_GRAY -> context.getString(R.string.disabled_behavior_gray_disabled)
                    else -> context.getString(R.string.disabled_behavior_do_not_differentiate)
                },
                onClick = { showDisabledBehaviorDialog = true }
            )

            if (disabledBehavior == ApplicationPreferences.DISABLED_BEHAVIOR_HIDE) {
                ClickablePreference(
                    title = context.getString(R.string.manage_disabled_users),
                    summary = context.getString(R.string.manage_disabled_users_summary),
                    enabled = disabledContactsCount > 0,
                    onClick = {
                        context.startActivity(Intent(context, ManageDisabledUsersActivity::class.java))
                    }
                )
            }

            SwitchPreference(
                title = context.getString(R.string.show_large_photo_for_contacts_that_has_events_in_the_near_future),
                summaryOn = showLargeNearSummaryOn,
                summaryOff = showLargeNearSummaryOff,
                checked = showLargePhotoNearFuture,
                onCheckedChange = { checked ->
                    ApplicationPreferences.putBoolean(context, ApplicationPreferences.SHOW_LARGE_PHOTO_FOR_CONTACTS_THAT_HAS_EVENTS_IN_THE_NEAR_FUTURE_KEY, checked)
                    showLargePhotoNearFuture = checked
                    if (!checked) {
                        ApplicationPreferences.putBoolean(context, ApplicationPreferences.SHOW_LARGE_PHOTO_FOR_CONTACTS_THAT_DO_NOT_HAS_EVENTS_IN_THE_NEAR_FUTURE_KEY, false)
                        showLargePhotoNoNearFuture = false
                    }
                }
            )

            SwitchPreference(
                title = context.getString(R.string.show_large_photo_for_contacts_that_do_not_has_events_in_the_near_future),
                summaryOn = showLargeNoNearSummaryOn,
                summaryOff = showLargeNoNearSummaryOff,
                checked = showLargePhotoNoNearFuture,
                enabled = showLargePhotoNearFuture,
                onCheckedChange = { checked ->
                    ApplicationPreferences.putBoolean(context, ApplicationPreferences.SHOW_LARGE_PHOTO_FOR_CONTACTS_THAT_DO_NOT_HAS_EVENTS_IN_THE_NEAR_FUTURE_KEY, checked)
                    showLargePhotoNoNearFuture = checked
                }
            )

            SwitchPreference(
                title = context.getString(R.string.add_header_for_today_and_tomorrow_events),
                summaryOn = context.getString(R.string.add_header_for_today_and_tomorrow_events_on_summary),
                summaryOff = context.getString(R.string.add_header_for_today_and_tomorrow_events_off_summary),
                checked = addHeaderTodayTomorrow,
                onCheckedChange = { checked ->
                    ApplicationPreferences.putBoolean(context, ApplicationPreferences.ADD_HEADER_FOR_YESTERDAY_TODAY_AND_TOMORROW_EVENTS_KEY, checked)
                    addHeaderTodayTomorrow = checked
                }
            )

            val now = Date().time
            ClickablePreference(
                title = context.getString(R.string.date_format),
                summary = when (dateFormat) {
                    ApplicationPreferences.DATE_FORMAT_SHORT -> context.getString(R.string.date_format_short, DateTimeUtilities.getDateString(FormatStyle.SHORT.ordinal, now))
                    ApplicationPreferences.DATE_FORMAT_MEDIUM -> context.getString(R.string.date_format_medium, DateTimeUtilities.getDateString(FormatStyle.MEDIUM.ordinal, now))
                    else -> context.getString(R.string.date_format_long, DateTimeUtilities.getDateString(FormatStyle.LONG.ordinal, now))
                },
                onClick = { showDateFormatDialog = true }
            )

            SectionHeader(context.getString(R.string.theme))

            ClickablePreference(
                title = context.getString(R.string.theme_mode),
                summary = context.getString(R.string.theme_mode_summary, when (themeMode) {
                    "light" -> context.getString(R.string.theme_mode_light)
                    "dark" -> context.getString(R.string.theme_mode_dark)
                    else -> context.getString(R.string.theme_mode_system)
                }),
                onClick = {
                    showThemeModeDialog = true
                }
            )

            val isDarkMode = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }

            SwitchPreference(
                title = context.getString(R.string.pure_black),
                summaryOn = context.getString(R.string.pure_black_summary),
                summaryOff = context.getString(R.string.pure_black_summary),
                checked = pureBlackBackground,
                enabled = isDarkMode,
                onCheckedChange = { checked ->
                    ApplicationPreferences.putBoolean(context, ApplicationPreferences.PURE_BLACK_BACKGROUND_KEY, checked)
                    pureBlackBackground = checked
                    onThemeChanged()
                    (context as? Activity)?.recreate()
                }
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SwitchPreference(
                    title = context.getString(R.string.system_accent),
                    summaryOn = context.getString(R.string.system_accent_summary),
                    summaryOff = context.getString(R.string.system_accent_summary),
                    checked = useSystemAccent,
                    onCheckedChange = { checked ->
                        ApplicationPreferences.putBoolean(context, ApplicationPreferences.USE_SYSTEM_ACCENT_KEY, checked)
                        useSystemAccent = checked
                        onThemeChanged()
                        (context as? Activity)?.recreate()
                    }
                )
            }

            SectionHeader(context.getString(R.string.about))

            ClickablePreference(
                title = context.getString(R.string.github_repository),
                summary = context.getString(R.string.github_repository_summary),
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, context.getString(R.string.github_repo).toUri())
                    context.startActivity(intent)
                }
            )

            InfoPreference(
                title = context.getString(R.string.app_version_name),
                summary = context.getString(R.string.app_version_name_summary, BuildConfig.versionName, BuildConfig.versionCode)
            )

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showNotNotifiedAccountsDialog) {
        NotNotifiedAccountsDialog(
            context = context,
            onDismiss = { showNotNotifiedAccountsDialog = false },
            onConfirm = { selection ->
                ApplicationPreferences.putStrings(context, ApplicationPreferences.NOT_NOTIFIED_ACCOUNTS_KEY, selection)
                showNotNotifiedAccountsDialog = false
                notNotifiedAccountsRefreshKey++
            }
        )
    }

    if (showNotificationDaysLeftDialog) {
        NotificationDaysLeftDialog(
            context = context,
            currentValue = notificationDaysLeftValue,
            onDismiss = { showNotificationDaysLeftDialog = false },
            onConfirm = { value ->
                ApplicationPreferences.putInteger(context, ApplicationPreferences.NOTIFICATION_DAYS_LEFT_KEY, value)
                notificationDaysLeftValue = value
                showNotificationDaysLeftDialog = false
            }
        )
    }

    if (showNotificationHourDialog) {
        val initialHourTime = remember { ApplicationPreferences.getString(context, ApplicationPreferences.NOTIFICATION_HOUR_KEY, ApplicationPreferences.NOTIFICATION_HOUR_DEFAULT) ?: ApplicationPreferences.NOTIFICATION_HOUR_DEFAULT }
        NotificationHourDialog(
            context = context,
            initialTime = initialHourTime,
            onDismiss = { showNotificationHourDialog = false },
            onConfirm = { time ->
                ApplicationPreferences.putString(context, ApplicationPreferences.NOTIFICATION_HOUR_KEY, time)
                MainService.schedule(context)
                showNotificationHourDialog = false
            }
        )
    }

    if (showDisabledBehaviorDialog) {
        DisabledBehaviorDialog(
            currentValue = disabledBehavior,
            context = context,
            onDismiss = { showDisabledBehaviorDialog = false },
            onConfirm = { value ->
                ApplicationPreferences.putString(context, ApplicationPreferences.DISABLED_BEHAVIOR_KEY, value)
                disabledBehavior = value
                showDisabledBehaviorDialog = false
            }
        )
    }

    if (showDateFormatDialog) {
        DateFormatDialog(
            currentValue = dateFormat,
            context = context,
            onDismiss = { showDateFormatDialog = false },
            onConfirm = { value ->
                ApplicationPreferences.putString(context, ApplicationPreferences.DATE_FORMAT_KEY, value)
                dateFormat = value
                showDateFormatDialog = false
            }
        )
    }

    if (showThemeModeDialog) {
        ThemeModeDialog(
            currentMode = themeMode,
            context = context,
            onDismiss = { showThemeModeDialog = false },
            onConfirm = { mode ->
                ApplicationPreferences.putString(context, ApplicationPreferences.THEME_STYLE_KEY, mode)
                themeMode = mode
                onThemeChanged()
                showThemeModeDialog = false
                (context as? Activity)?.recreate()
            }
        )
    }
}

@Composable
private fun InfoPreference(
    title: String,
    summary: String?,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
            }
        }
    }
}

@Composable
private fun ThemeModeDialog(
    currentMode: String,
    context: Context,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val modes = listOf(
        "system" to context.getString(R.string.theme_mode_system),
        "light" to context.getString(R.string.theme_mode_light),
        "dark" to context.getString(R.string.theme_mode_dark)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.theme_mode)) },
        text = {
            LazyColumn {
                itemsIndexed(modes) { _, (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onConfirm(value) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentMode == value,
                            onClick = { onConfirm(value) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(context.getString(R.string.cancel_button)) }
        }
    )
}

@Composable
private fun DisabledBehaviorDialog(
    currentValue: String,
    context: Context,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val options = listOf(
        ApplicationPreferences.DISABLED_BEHAVIOR_HIDE to context.getString(R.string.disabled_behavior_hide_disabled),
        ApplicationPreferences.DISABLED_BEHAVIOR_GRAY to context.getString(R.string.disabled_behavior_gray_disabled),
        ApplicationPreferences.DISABLED_BEHAVIOR_NONE to context.getString(R.string.disabled_behavior_do_not_differentiate)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.disabled_behavior)) },
        text = {
            LazyColumn {
                itemsIndexed(options) { _, (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onConfirm(value) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentValue == value,
                            onClick = { onConfirm(value) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(context.getString(R.string.cancel_button)) }
        }
    )
}

@Composable
private fun DateFormatDialog(
    currentValue: String,
    context: Context,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val now = Date().time
    val options = listOf(
        ApplicationPreferences.DATE_FORMAT_SHORT to context.getString(R.string.date_format_short, DateTimeUtilities.getDateString(FormatStyle.SHORT.ordinal, now)),
        ApplicationPreferences.DATE_FORMAT_MEDIUM to context.getString(R.string.date_format_medium, DateTimeUtilities.getDateString(FormatStyle.MEDIUM.ordinal, now)),
        ApplicationPreferences.DATE_FORMAT_LONG to context.getString(R.string.date_format_long, DateTimeUtilities.getDateString(FormatStyle.LONG.ordinal, now))
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.date_format)) },
        text = {
            LazyColumn {
                itemsIndexed(options) { _, (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onConfirm(value) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentValue == value,
                            onClick = { onConfirm(value) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(context.getString(R.string.cancel_button)) }
        }
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SwitchPreference(
    title: String,
    summaryOn: String = "",
    summaryOff: String = "",
    summaryOverride: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    val displaySummary = summaryOverride ?: if (checked) summaryOn else summaryOff
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            if (displaySummary.isNotEmpty()) {
                Text(
                    text = displaySummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun ClickablePreference(
    title: String,
    summary: String?,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
            }
        }
    }
}

@SuppressLint("MutableCollectionMutableState")
@Composable
private fun NotNotifiedAccountsDialog(
    context: Context,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    val accountsNames = remember { DeviceContactsDriver.getAccounts(context) ?: emptyList() }
    val additionalDescriptions = listOf(context.getString(R.string.local_contacts), context.getString(R.string.app_contacts))
    val additionalValues = listOf(ApplicationPreferences.LOCAL_CONTACTS_ACCOUNT_NAME, ApplicationPreferences.APP_CONTACTS_ACCOUNT_NAME)
    val allDescriptions = remember { accountsNames + additionalDescriptions }
    val allValues = remember { accountsNames + additionalValues }
    val initialSelection = remember {
        ApplicationPreferences.getStrings(context, ApplicationPreferences.NOT_NOTIFIED_ACCOUNTS_KEY, ApplicationPreferences.NOT_NOTIFIED_ACCOUNTS_DEFAULT) ?: emptySet()
    }
    val selection = remember { mutableStateOf(initialSelection.toMutableSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.not_notified_accounts_title)) },
        text = {
            LazyColumn {
                itemsIndexed(allDescriptions) { index, description ->
                    val value = allValues[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val newSet = selection.value.toMutableSet()
                                if (newSet.contains(value)) newSet.remove(value)
                                else newSet.add(value)
                                selection.value = newSet
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = description
                        )
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = !selection.value.contains(value),
                            onCheckedChange = { checked ->
                                val newSet = selection.value.toMutableSet()
                                if (checked) newSet.remove(value)
                                else newSet.add(value)
                                selection.value = newSet
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selection.value) }) { Text(context.getString(R.string.accept_button)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(context.getString(R.string.cancel_button)) }
        }
    )
}

@Composable
private fun NotificationDaysLeftDialog(
    context: Context,
    currentValue: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var textValue by remember { mutableStateOf(currentValue.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.notification_days_left)) },
        text = {
            Column {
                Text(context.getString(R.string.notification_days_left))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { newValue ->
                        val filtered = newValue.filter { it.isDigit() }
                        textValue = filtered
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = context.getString(R.string.days),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val value = textValue.toInt().coerceIn(NOTIFICATION_DAYS_LEFT_MIN, NOTIFICATION_DAYS_LEFT_MAX)
                onConfirm(value)
            }) { Text(context.getString(R.string.accept_button)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(context.getString(R.string.cancel_button)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationHourDialog(
    context: Context,
    initialTime: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val parts = remember {
        val p = initialTime.split(":")
        Pair(
            p.getOrNull(0)?.toIntOrNull() ?: 0,
            p.getOrNull(1)?.toIntOrNull() ?: 0
        )
    }
    val state = rememberTimePickerState(
        initialHour = parts.first,
        initialMinute = parts.second,
        is24Hour = DateFormat.is24HourFormat(context)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(context.getString(R.string.hour_value, state.hour, state.minute))
            }) { Text(context.getString(R.string.accept_button)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(context.getString(R.string.cancel_button)) }
        }
    )
}
