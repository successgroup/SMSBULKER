package com.gscube.smsbulker.di

import android.content.Context
import com.gscube.smsbulker.di.AppScope
import com.gscube.smsbulker.utils.SecureStorage
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import javax.inject.Named
import javax.inject.Singleton

@Module
@ContributesTo(AppScope::class)
class StorageModule {
    companion object {
        @Provides
        @Singleton
        fun provideSecureStorage(@Named("applicationContext") context: Context): SecureStorage {
            return SecureStorage(context)
        }
    }
}
