package com.gscube.smsbulker.di

import android.app.Application
import android.content.Context
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import javax.inject.Named
import javax.inject.Singleton

@Module
@ContributesTo(AppScope::class)
object ApplicationModule {
    @Provides
    @Singleton
    @Named("applicationContext")
    fun provideApplicationContext(application: Application): Context = application.applicationContext
}
