package com.gscube.smsbulker.di

import com.gscube.smsbulker.repository.AITemplateRepository
import com.gscube.smsbulker.repository.impl.MockAITemplateRepositoryImpl
import com.squareup.anvil.annotations.ContributesTo
import dagger.Binds
import dagger.Module
import javax.inject.Singleton

@Module
@ContributesTo(AppScope::class)
abstract class AITemplateModule {
    // Binding for AITemplateRepository is now handled by @ContributesBinding on MockAITemplateRepositoryImpl
}
