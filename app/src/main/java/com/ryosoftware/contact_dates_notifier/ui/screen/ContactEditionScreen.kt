package com.ryosoftware.contact_dates_notifier.ui.screen

import android.app.DatePickerDialog
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.Toast
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.ryosoftware.contact_dates_notifier.data.ApplicationPreferences
import com.ryosoftware.contact_dates_notifier.data.model.ApplicationContactEvent
import com.ryosoftware.contact_dates_notifier.ui.viewmodel.ContactEditionViewModel
import com.ryosoftware.contact_dates_notifier.R
import java.io.File
import java.util.Calendar

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactEditionScreen(
    viewModel: ContactEditionViewModel,
    contactId: Long?,
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(contactId) {
        if (contactId != null && contactId >= 0) viewModel.loadContact(contactId)
        else viewModel.createContact()
    }

    LaunchedEffect(state.savedSuccessfully) {
        if (state.savedSuccessfully) onSaved()
    }

    var showEventDialog by remember { mutableStateOf<ApplicationContactEvent?>(null) }
    var showPhotoPickerDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var eventToDelete by remember { mutableStateOf<ApplicationContactEvent?>(null) }
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }

    BackHandler(state.isDirty) {
        showUnsavedDialog = true
    }

    val context = LocalContext.current
    val useSystemAccent = remember { ApplicationPreferences.getBoolean(context, ApplicationPreferences.USE_SYSTEM_ACCENT_KEY, ApplicationPreferences.USE_SYSTEM_ACCENT_DEFAULT) }
    val accentColor = if (useSystemAccent) MaterialTheme.colorScheme.primary else colorResource(R.color.primary)

    fun loadDrawable(uri: Uri) {
        val inputStream = context.contentResolver.openInputStream(uri)
        val drawable = inputStream?.use { stream ->
            val bytes = stream.readBytes()
            val photoFile = File(context.cacheDir, "contact_photo_${System.currentTimeMillis()}.jpg")
            photoFile.writeBytes(bytes)
            viewModel.setNewPhoto(photoFile)
            BitmapDrawable(context.resources, BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        }
        viewModel.setPhoto(drawable)
    }

    val cropLauncher = rememberLauncherForActivityResult(
        contract = CropImageContract()
    ) { result ->
        if (result.isSuccessful) {
            result.uriContent?.let { loadDrawable(it) }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            cropLauncher.launch(
                CropImageContractOptions(
                    uri = it,
                    cropImageOptions = CropImageOptions(
                        guidelines = CropImageView.Guidelines.ON
                    )
                )
            )
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            cameraImageUri?.let {
                cropLauncher.launch(
                    CropImageContractOptions(
                        uri = it,
                        cropImageOptions = CropImageOptions(
                            guidelines = CropImageView.Guidelines.ON
                        )
                    )
                )
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        if (granted) {
            val photoFile = File(context.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
            cameraImageUri = uri
            cameraLauncher.launch(uri)
        } else {
            Toast.makeText(context, context.getString(R.string.camera_permission_denied), Toast.LENGTH_SHORT).show()
        }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    Scaffold(
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(if (contactId == null) stringResource(R.string.new_contact) else stringResource(R.string.edit_contact))
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.isDirty) showUnsavedDialog = true else onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.save() }) {
                        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.save))
                    }
                    if (contactId != null) {
                        IconButton(onClick = { showDeleteConfirmDialog = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.delete)
                            )
                        }
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
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    item {
                        PhotoSection(
                            photo = state.photo,
                            name = state.name,
                            onPhotoClick = { showPhotoPickerDialog = true }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    item {
                        NameSection(
                            name = state.name,
                            onNameChange = { viewModel.updateName(it) }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    item {
                        EventsHeader(
                            onAddClick = { showEventDialog = ApplicationContactEvent(description = "", day = 0, month = 0, year = 0) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    itemsIndexed(state.events) { _, event ->
                        EventRow(
                            event = event,
                            onClick = { showEventDialog = event },
                            onDeleteClick = { eventToDelete = event }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (state.isSaving) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }

    showEventDialog?.let { event ->
        EventEditDialog(
            initialDescription = event.description,
            initialDay = event.day,
            initialMonth = event.month,
            initialYear = event.year,
            onDismiss = { showEventDialog = null },
            onConfirm = { desc, day, month, year ->
                if (event.description.isEmpty() && event.day == 0 && event.month == 0) {
                    viewModel.addEvent(desc, day, month, year)
                } else {
                    viewModel.removeEvent(event)
                    viewModel.addEvent(desc, day, month, year)
                }
                showEventDialog = null
            }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(stringResource(R.string.delete_contact_title)) },
            text = { Text(stringResource(R.string.delete_contact_confirmation, state.name)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmDialog = false
                    viewModel.delete()
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel_button))
                }
            }
        )
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            text = { Text(stringResource(R.string.unsaved_changes_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showUnsavedDialog = false
                    onNavigateBack()
                }) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnsavedDialog = false }) {
                    Text(stringResource(R.string.continue_editing))
                }
            }
        )
    }

    eventToDelete?.let { event ->
        AlertDialog(
            onDismissRequest = { eventToDelete = null },
            title = { Text(stringResource(R.string.delete_event_title)) },
            text = { Text(stringResource(R.string.delete_event_confirmation, event.description)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeEvent(event)
                    eventToDelete = null
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { eventToDelete = null }) {
                    Text(stringResource(R.string.cancel_button))
                }
            }
        )
    }

    if (showPhotoPickerDialog) {
        ModalBottomSheet(
            onDismissRequest = { showPhotoPickerDialog = false }
        ) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(
                    text = stringResource(R.string.tap_to_change_photo),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showPhotoPickerDialog = false
                            galleryLauncher.launch("image/*")
                        }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.select_from_gallery), style = MaterialTheme.typography.bodyLarge)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                showPhotoPickerDialog = false
                                val photoFile = File(context.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
                                cameraImageUri = uri
                                cameraLauncher.launch(uri)
                            } else {
                                showPhotoPickerDialog = false
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.take_photo), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun PhotoSection(
    photo: Drawable?,
    name: String,
    onPhotoClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(CircleShape)
                .clickable(onClick = onPhotoClick)
        ) {
            if (photo != null) {
                val bitmap = (photo as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    PhotoPlaceholder(name = name)
                }
            } else {
                PhotoPlaceholder(name = name)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.tap_to_change_photo),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PhotoPlaceholder(name: String) {
    Box(
        modifier = Modifier.fillMaxSize().clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.firstOrNull()?.uppercase() ?: stringResource(R.string.no_contact_photo_letter),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NameSection(
    name: String,
    onNameChange: (String) -> Unit
) {
    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text(stringResource(R.string.name_section)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun EventsHeader(onAddClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.special_dates_section),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconButton(onClick = onAddClick) {
            Icon(
                Icons.Filled.Add,
                contentDescription = stringResource(R.string.add_event),
                modifier = Modifier.size(18.dp)
            )
        }
    }
    HorizontalDivider()
}

@Composable
private fun EventRow(
    event: ApplicationContactEvent,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.description,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = DateFormat.getDateFormat(context).format(
                    Calendar.getInstance().apply {
                        set(Calendar.YEAR, event.year)
                        set(Calendar.MONTH, event.month - 1)
                        set(Calendar.DAY_OF_MONTH, event.day)
                    }.time
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDeleteClick) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.delete_event),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun EventEditDialog(
    initialDescription: String,
    initialDay: Int,
    initialMonth: Int,
    initialYear: Int,
    onDismiss: () -> Unit,
    onConfirm: (String, Int, Int, Int) -> Unit
) {
    var description by remember { mutableStateOf(initialDescription) }
    var day by remember { mutableIntStateOf(initialDay) }
    var month by remember { mutableIntStateOf(initialMonth) }
    var year by remember { mutableIntStateOf(if (initialYear > 0) initialYear else Calendar.getInstance().get(Calendar.YEAR)) }
    val context = LocalContext.current
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.event)) },
        text = {
            Column {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.description)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }) {
                    OutlinedTextField(
                        value = if (day > 0) DateFormat.getDateFormat(context).format(
                            Calendar.getInstance().apply {
                                set(Calendar.YEAR, year)
                                set(Calendar.MONTH, month - 1)
                                set(Calendar.DAY_OF_MONTH, day)
                            }.time
                        ) else stringResource(R.string.select_date),
                        onValueChange = {},
                        label = { Text(stringResource(R.string.date)) },
                        readOnly = true,
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(description, day, month, year) },
                enabled = description.isNotBlank()
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_button))
            }
        }
    )

    if (showDatePicker) {
        val datePickerDialog = remember {
            DatePickerDialog(
                context,
                { _, y, m, d ->
                    day = d
                    month = m + 1
                    year = y
                    showDatePicker = false
                },
                year,
                month - 1,
                day
            )
        }
        DisposableEffect(Unit) {
            datePickerDialog.show()
            onDispose {
                datePickerDialog.dismiss()
            }
        }
    }
}
