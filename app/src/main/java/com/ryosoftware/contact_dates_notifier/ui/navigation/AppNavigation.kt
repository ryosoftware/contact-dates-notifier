package com.ryosoftware.contact_dates_notifier.ui.navigation

import android.content.ContentUris
import android.content.Intent
import android.provider.ContactsContract
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ryosoftware.contact_dates_notifier.data.model.ApplicationContact
import com.ryosoftware.contact_dates_notifier.data.model.DeviceContact
import com.ryosoftware.contact_dates_notifier.ui.screen.ContactEditionScreen
import com.ryosoftware.contact_dates_notifier.ui.screen.MainScreen
import com.ryosoftware.contact_dates_notifier.ui.viewmodel.ContactEditionViewModel
import com.ryosoftware.contact_dates_notifier.ui.viewmodel.MainViewModel

object Routes {
    const val MAIN = "main"
    const val CONTACT_EDIT = "contact_edit/{contactId}"
    const val CONTACT_CREATE = "contact_create"

    fun contactEdit(contactId: Long) = "contact_edit/$contactId"
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    onSettingsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    NavHost(
        navController = navController,
        startDestination = Routes.MAIN
    ) {
        composable(Routes.MAIN) {
            MainScreen(
                viewModel = mainViewModel,
                onAddContact = { navController.navigate(Routes.CONTACT_CREATE) },
                onContactClick = { contact ->
                    when (contact) {
                        is ApplicationContact -> {
                            if (contact.id >= 0) {
                                navController.navigate(Routes.contactEdit(contact.id))
                            }
                        }
                        is DeviceContact -> {
                            val uri = ContentUris.withAppendedId(
                                ContactsContract.Contacts.CONTENT_URI,
                                contact.databaseContactIdentifier
                            )
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, uri)
                            )
                        }
                    }
                },
                onSettingsClick = onSettingsClick
            )
        }

        composable(
            route = Routes.CONTACT_EDIT,
            arguments = listOf(navArgument("contactId") { type = NavType.LongType })
        ) { backStackEntry ->
            val contactId = backStackEntry.arguments?.getLong("contactId") ?: -1L
            val editionViewModel: ContactEditionViewModel = viewModel()
            ContactEditionScreen(
                viewModel = editionViewModel,
                contactId = contactId,
                onNavigateBack = { navController.popBackStack() },
                onSaved = {
                    mainViewModel.loadContacts()
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.CONTACT_CREATE) {
            val editionViewModel: ContactEditionViewModel = viewModel()
            ContactEditionScreen(
                viewModel = editionViewModel,
                contactId = null,
                onNavigateBack = { navController.popBackStack() },
                onSaved = {
                    mainViewModel.loadContacts()
                    navController.popBackStack()
                }
            )
        }
    }
}
