package com.gscube.smsbulker.di

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import com.gscube.smsbulker.MainActivity
import com.gscube.smsbulker.SmsBulkerApplication
import com.gscube.smsbulker.ui.account.AccountFragment
import com.gscube.smsbulker.ui.analytics.AnalyticsFragment
import com.gscube.smsbulker.ui.auth.LoginActivity
import com.gscube.smsbulker.ui.auth.LoginFragment
import com.gscube.smsbulker.ui.auth.SignupActivity
import com.gscube.smsbulker.ui.auth.SignupFragment
import com.gscube.smsbulker.ui.contacts.ContactsFragment
import com.gscube.smsbulker.ui.csvEditor.CsvEditorFragment
import com.gscube.smsbulker.ui.home.HomeFragment
import com.gscube.smsbulker.ui.templates.PreviewTemplateDialog
import com.gscube.smsbulker.ui.templates.TemplatesFragment
import com.gscube.smsbulker.ui.test.TestSmsActivity
import com.squareup.anvil.annotations.MergeComponent
import dagger.BindsInstance
import dagger.Component
import javax.inject.Singleton

@Singleton
@MergeComponent(
    scope = AppScope::class,
    modules = [AppModuleContributions::class]
)
interface AppComponent : ViewModelComponent {
    fun viewModelFactory(): ViewModelProvider.Factory

    fun inject(application: SmsBulkerApplication)
    fun inject(activity: MainActivity)
    fun inject(fragment: LoginFragment)
    fun inject(activity: LoginActivity)
    fun inject(activity: SignupActivity)
    fun inject(fragment: SignupFragment)
    fun inject(fragment: TemplatesFragment)
    fun inject(fragment: HomeFragment)
    fun inject(fragment: AccountFragment)
    fun inject(fragment: AnalyticsFragment)
    fun inject(fragment: ContactsFragment)
    fun inject(fragment: CsvEditorFragment)
    fun inject(dialog: PreviewTemplateDialog)
    fun inject(activity: TestSmsActivity)

    @Component.Factory
    interface Factory {
        fun create(@BindsInstance application: Application): AppComponent
    }
}
