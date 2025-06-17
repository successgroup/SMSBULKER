package com.gscube.smsbulker.di

import android.content.Context
import com.gscube.smsbulker.BuildConfig
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import javax.inject.Named
import javax.inject.Singleton

@Module
@ContributesTo(AppScope::class)
object ConfigModule {
    @Provides
    @Singleton
    @Named("apiKey")
    fun provideApiKey(): String {
        // In production, this should be stored securely or fetched from a secure source
        return BuildConfig.API_KEY // We'll add this to build.gradle.kts
    }
}
