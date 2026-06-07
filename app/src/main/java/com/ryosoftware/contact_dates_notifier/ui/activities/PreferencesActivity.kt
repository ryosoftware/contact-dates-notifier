package com.ryosoftware.contact_dates_notifier.ui.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.material3.ExperimentalMaterial3Api
import com.ryosoftware.contact_dates_notifier.ui.screen.PreferencesContent
import com.ryosoftware.contact_dates_notifier.ui.theme.BirthdaysNotifierTheme

@OptIn(ExperimentalMaterial3Api::class)
class PreferencesActivity : ComponentActivity() {

    companion object {
        const val THEME_CHANGED_EXTRA = "theme_changed"
    }

    private var themeChanged = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_OK)

        setContent {
            BirthdaysNotifierTheme(activity = this@PreferencesActivity) {
                PreferencesContent(
                    context = this@PreferencesActivity,
                    onThemeChanged = { themeChanged = true }
                )
            }
        }

        onBackPressedDispatcher.addCallback(this) {
            if (themeChanged) {
                setResult(Activity.RESULT_OK, Intent().putExtra(THEME_CHANGED_EXTRA, true))
            }
            finish()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
