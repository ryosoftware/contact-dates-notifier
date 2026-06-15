package com.ryosoftware.contact_dates_notifier.ui.activities

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.ryosoftware.contact_dates_notifier.R
import com.ryosoftware.contact_dates_notifier.data.ApplicationPreferences
import com.ryosoftware.contact_dates_notifier.ui.navigation.AppNavHost
import com.ryosoftware.contact_dates_notifier.ui.theme.BirthdaysNotifierTheme
import com.ryosoftware.contact_dates_notifier.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@android.annotation.SuppressLint("CustomSplashScreen")
class AppActivity : ComponentActivity() {

    companion object {
        private var iRunning = false

        fun isRunning(): Boolean = iRunning
    }

    private lateinit var mainViewModel: MainViewModel
    private var needsExactAlarmPermissionDialog by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        loadContacts()
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data?.getBooleanExtra(PreferencesActivity.THEME_CHANGED_EXTRA, false) == true) {
            recreate()
        }
    }

    private fun loadContacts() {
        lifecycleScope.launch {
            mainViewModel.loadContacts()
        }
    }

    override fun onResume() {
        super.onResume()
        iRunning = true
    }

    override fun onPause() {
        super.onPause()
        iRunning = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainViewModel = ViewModelProvider(this)[MainViewModel::class.java]
        if (!checkInitialPermissions()) loadContacts()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ApplicationPreferences.getBoolean(this, ApplicationPreferences.ENABLE_NOTIFICATIONS_KEY, ApplicationPreferences.ENABLE_NOTIFICATIONS_DEFAULT)) {
                val alarmManager = getSystemService(AlarmManager::class.java)
                if (!alarmManager.canScheduleExactAlarms()) {
                    needsExactAlarmPermissionDialog = true
                }
            }
        }

        setContent {
            BirthdaysNotifierTheme(activity = this@AppActivity) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (needsExactAlarmPermissionDialog) {
                        AlertDialog(
                            onDismissRequest = { needsExactAlarmPermissionDialog = false },
                            title = { Text(stringResource(R.string.exact_alarm_permission_title)) },
                            text = { Text(stringResource(R.string.exact_alarm_permission_message)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    needsExactAlarmPermissionDialog = false
                                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                        data = Uri.parse("package:${this@AppActivity.packageName}")
                                    }
                                    this@AppActivity.startActivity(intent)
                                }) {
                                    Text(stringResource(R.string.go_to_settings))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { needsExactAlarmPermissionDialog = false }) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                        )
                    }
                    val navController = rememberNavController()
                    AppNavHost(
                        navController = navController,
                        mainViewModel = mainViewModel,
                        onSettingsClick = { settingsLauncher.launch(Intent(this@AppActivity, PreferencesActivity::class.java)) }
                    )
                }
            }
        }
    }

    private fun checkInitialPermissions(): Boolean {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) permissions.add(Manifest.permission.READ_CONTACTS)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.GET_ACCOUNTS)
            != PackageManager.PERMISSION_GRANTED
        ) permissions.add(Manifest.permission.GET_ACCOUNTS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ApplicationPreferences.getBoolean(this, ApplicationPreferences.ENABLE_NOTIFICATIONS_KEY, ApplicationPreferences.ENABLE_NOTIFICATIONS_DEFAULT) &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
            true
        } else {
            false
        }
    }
}
