package com.gscube.smsbulker.di

import com.gscube.smsbulker.ui.account.AccountViewModel
import com.gscube.smsbulker.ui.analytics.AnalyticsViewModel
import com.gscube.smsbulker.ui.auth.AuthViewModel
import com.gscube.smsbulker.ui.contacts.ContactsViewModel
import com.gscube.smsbulker.ui.csvEditor.CsvEditorViewModel
import com.gscube.smsbulker.ui.home.HomeViewModel
import com.gscube.smsbulker.ui.sendMessage.SendMessageViewModel
import com.gscube.smsbulker.ui.templates.TemplatesViewModel
import com.squareup.anvil.annotations.ContributesTo

@ContributesTo(AppScope::class)
interface ViewModelComponent {
    fun authViewModel(): AuthViewModel
    fun accountViewModel(): AccountViewModel
    fun analyticsViewModel(): AnalyticsViewModel
    fun contactsViewModel(): ContactsViewModel
    fun homeViewModel(): HomeViewModel
    fun templatesViewModel(): TemplatesViewModel
    fun sendMessageViewModel(): SendMessageViewModel
    fun csvEditorViewModel(): CsvEditorViewModel
}
