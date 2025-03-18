package com.gscube.smsbulker.di

import androidx.lifecycle.ViewModelProvider
import com.squareup.anvil.annotations.ContributesTo

@ContributesTo(AppScope::class)
interface ViewModelComponent {
    fun viewModelFactory(): ViewModelProvider.Factory
}
