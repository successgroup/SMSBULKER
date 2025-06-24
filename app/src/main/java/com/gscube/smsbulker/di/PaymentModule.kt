package com.gscube.smsbulker.di

import com.squareup.anvil.annotations.ContributesTo
import dagger.Module

@Module
@ContributesTo(AppScope::class)
abstract class PaymentModule {
    // Binding is now handled by @ContributesBinding on PaymentRepositoryImpl
}