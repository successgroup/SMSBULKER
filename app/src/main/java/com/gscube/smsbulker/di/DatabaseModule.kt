package com.gscube.smsbulker.di

import android.content.Context
import com.gscube.smsbulker.data.local.TemplateDatabase
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import javax.inject.Named
import javax.inject.Singleton

@Module
@ContributesTo(AppScope::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideTemplateDatabase(@Named("applicationContext") context: Context): TemplateDatabase {
        return TemplateDatabase(context)
    }
}