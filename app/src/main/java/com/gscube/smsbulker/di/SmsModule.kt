package com.gscube.smsbulker.di

import com.gscube.smsbulker.data.network.ArkeselApi
import com.gscube.smsbulker.repository.SmsRepository
import com.gscube.smsbulker.repository.impl.SmsRepositoryImpl
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import javax.inject.Named
import javax.inject.Singleton

@Module
@ContributesTo(AppScope::class)
object SmsModule {
    // Removed provideSmsRepository since Anvil generates the binding
}