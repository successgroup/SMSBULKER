package com.gscube.smsbulker.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.gscube.smsbulker.ui.account.AccountViewModel
import com.gscube.smsbulker.ui.analytics.AnalyticsViewModel
import com.gscube.smsbulker.ui.auth.AuthViewModel
import com.gscube.smsbulker.ui.contacts.ContactsViewModel
import com.gscube.smsbulker.ui.csvEditor.CsvEditorViewModel
import com.gscube.smsbulker.ui.home.HomeViewModel
import com.gscube.smsbulker.ui.settings.SettingsViewModel
import com.gscube.smsbulker.ui.templates.TemplatesViewModel
import com.gscube.smsbulker.ui.sendMessage.SendMessageViewModel
import com.gscube.smsbulker.ui.sms.SmsViewModel
import com.gscube.smsbulker.ui.payment.PaymentViewModel
import com.gscube.smsbulker.ui.templates.AITemplateViewModel
import com.squareup.anvil.annotations.ContributesTo
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap
import javax.inject.Provider

@Module
@ContributesTo(AppScope::class)
abstract class ViewModelModule {
    @Binds
    abstract fun bindViewModelFactory(factory: ViewModelFactory): ViewModelProvider.Factory

    @Binds
    @IntoMap
    @ViewModelKey(AuthViewModel::class)
    abstract fun bindAuthViewModel(viewModel: AuthViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(AccountViewModel::class)
    abstract fun bindAccountViewModel(viewModel: AccountViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(AnalyticsViewModel::class)
    abstract fun bindAnalyticsViewModel(viewModel: AnalyticsViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ContactsViewModel::class)
    abstract fun bindContactsViewModel(viewModel: ContactsViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(TemplatesViewModel::class)
    abstract fun bindTemplatesViewModel(viewModel: TemplatesViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(CsvEditorViewModel::class)
    abstract fun bindCsvEditorViewModel(viewModel: CsvEditorViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(SendMessageViewModel::class)
    abstract fun bindSendMessageViewModel(viewModel: SendMessageViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(SmsViewModel::class)
    abstract fun bindSmsViewModel(viewModel: SmsViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(SettingsViewModel::class)
    abstract fun bindSettingsViewModel(viewModel: SettingsViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(PaymentViewModel::class)
    abstract fun bindPaymentViewModel(viewModel: PaymentViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(AITemplateViewModel::class)
    abstract fun bindAITemplateViewModel(viewModel: AITemplateViewModel): ViewModel
    
    @Binds
    @IntoMap
    @ViewModelKey(HomeViewModel::class)
    abstract fun bindHomeViewModel(viewModel: HomeViewModel): ViewModel
}
