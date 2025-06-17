package com.gscube.smsbulker.di

import com.gscube.smsbulker.repository.PaymentRepository
import com.gscube.smsbulker.repository.impl.PaymentRepositoryImpl
import com.squareup.anvil.annotations.ContributesTo
import dagger.Binds
import dagger.Module

@Module
@ContributesTo(AppScope::class)
abstract class PaymentModule {
    
    @Binds
    abstract fun bindPaymentRepository(impl: PaymentRepositoryImpl): PaymentRepository
}