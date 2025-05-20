package com.gscube.smsbulker

import android.app.Application
import com.gscube.smsbulker.di.AppComponent
import com.gscube.smsbulker.di.DaggerAppComponent
import com.gscube.smsbulker.utils.NetworkUtils
import com.gscube.smsbulker.utils.SecureStorage
import javax.inject.Inject

class SmsBulkerApplication : Application() {
    
    @Inject
    lateinit var secureStorage: SecureStorage
    
    @Inject
    lateinit var networkUtils: NetworkUtils

    lateinit var appComponent: AppComponent
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        appComponent = DaggerAppComponent.factory().create(this)
        appComponent.inject(this)
        
        // Initialize API key if not already set
        if (secureStorage.getApiKey().isNullOrEmpty()) {
            secureStorage.saveAuthData(
                userId = "test_user",
                apiKey = "ZnhoSWFRbWhBWmpIc3N3eUNEZW8",
                email = "test@example.com",
                name = "Test User",
                companyName = "CEGraceland",
                companyAlias = "CEGraceland"
            )
        }
    }

    companion object {
        lateinit var instance: SmsBulkerApplication
            private set
    }
}
