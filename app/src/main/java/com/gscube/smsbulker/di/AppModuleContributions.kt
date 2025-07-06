package com.gscube.smsbulker.di

import com.squareup.anvil.annotations.ContributesTo
import dagger.Module

@Module(
    includes = [
        AppModule::class,
        NetworkModule::class,
        DatabaseModule::class,
        SmsModule::class,
        FirebaseModule::class,
        AnalyticsModule::class,
        StorageModule::class,
        CsvModule::class,
        ViewModelModule::class,
        ConfigModule::class,
        AITemplateModule::class
    ]
)
@ContributesTo(AppScope::class)
interface AppModuleContributions
