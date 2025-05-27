package com.gscube.smsbulker.di

import com.gscube.smsbulker.service.DeliveryReportHandler
import com.squareup.anvil.annotations.ContributesTo
import dagger.Binds
import dagger.Module
import javax.inject.Singleton

@Module
@ContributesTo(AppScope::class)
interface ServiceModule {
    @Binds
    @Singleton
    fun bindDeliveryReportHandler(impl: DeliveryReportHandler): DeliveryReportHandler
}