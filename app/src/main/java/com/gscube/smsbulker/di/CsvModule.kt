package com.gscube.smsbulker.di

import com.gscube.smsbulker.ui.csvEditor.CsvAdapter
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
@ContributesTo(AppScope::class)
object CsvModule {
    @Provides
    @Singleton
    fun provideCsvAdapter(): CsvAdapter = CsvAdapter()
}
