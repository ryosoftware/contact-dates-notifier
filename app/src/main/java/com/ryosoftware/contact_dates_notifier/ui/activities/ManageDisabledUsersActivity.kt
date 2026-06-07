package com.ryosoftware.contact_dates_notifier.ui.activities

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.ExperimentalMaterial3Api
import com.ryosoftware.contact_dates_notifier.data.ApplicationPreferences
import com.ryosoftware.contact_dates_notifier.data.model.ApplicationContact
import com.ryosoftware.contact_dates_notifier.ui.screen.ManageDisabledUsersContent
import com.ryosoftware.contact_dates_notifier.ui.theme.BirthdaysNotifierTheme

@OptIn(ExperimentalMaterial3Api::class)
class ManageDisabledUsersActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_OK)

        setContent {
            BirthdaysNotifierTheme(activity = this@ManageDisabledUsersActivity) {
                ManageDisabledUsersContent(
                    context = this@ManageDisabledUsersActivity,
                    onBack = { finish() },
                    onRemoveContact = { contact ->
                        when (contact) {
                            is ApplicationContact -> {
                                contact.disabled = false
                                contact.setDisabled(this@ManageDisabledUsersActivity, false)
                            }
                            else -> ApplicationPreferences.Contacts.setDontDisplayAlertsFromThisContact(this@ManageDisabledUsersActivity, contact, false)
                        }
                    },
                    onRemoveEvent = { contact, eventKey ->
                        ApplicationPreferences.Contacts.removeDisabledEvent(this@ManageDisabledUsersActivity, contact, eventKey)
                    }
                )
            }
        }
    }
}
