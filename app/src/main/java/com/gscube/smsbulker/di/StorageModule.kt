package com.gscube.smsbulker.di

import android.app.Application
import com.gscube.smsbulker.utils.SecureStorage
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
@ContributesTo(AppScope::class)
object StorageModule {
    @Provides
    @Singleton
    fun provideSecureStorage(application: Application): SecureStorage {
        return SecureStorage(application)
    }
}
