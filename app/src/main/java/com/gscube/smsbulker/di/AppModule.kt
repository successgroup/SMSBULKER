package com.gscube.smsbulker.di

import android.app.Application
import android.content.Context
import com.gscube.smsbulker.SmsBulkerApplication
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import javax.inject.Named
import javax.inject.Singleton

@Module
@ContributesTo(AppScope::class)
object AppModule {
    @Provides
    @Singleton
    @Named("applicationContext")
    fun provideApplicationContext(application: Application): Context = application.applicationContext

    @Provides
    @Singleton
    fun provideSmsBulkerApplication(application: Application): SmsBulkerApplication = application as SmsBulkerApplication

    @Provides
    @Singleton
    fun provideContextForContactsViewModel(application: Application): Context = application.applicationContext
}