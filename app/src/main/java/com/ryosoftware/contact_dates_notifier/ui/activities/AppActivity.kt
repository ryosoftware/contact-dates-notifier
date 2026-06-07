package com.ryosoftware.contact_dates_notifier.ui.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
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

        setContent {
            BirthdaysNotifierTheme(activity = this@AppActivity) {
                Surface(modifier = Modifier.fillMaxSize()) {
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
