package com.gscube.smsbulker.di

import android.app.Application
import com.gscube.smsbulker.data.local.AppDatabase
import com.gscube.smsbulker.data.local.TemplateDatabase
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
@ContributesTo(AppScope::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideTemplateDatabase(application: Application): TemplateDatabase {
        return TemplateDatabase(application)
    }

    @Provides
    @Singleton
    fun provideAppDatabase(application: Application): AppDatabase {
        return AppDatabase.getInstance(application)
    }
}