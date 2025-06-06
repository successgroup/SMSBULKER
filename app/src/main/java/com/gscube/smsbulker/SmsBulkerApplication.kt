package com.gscube.smsbulker

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.gscube.smsbulker.di.AppComponent
import com.gscube.smsbulker.di.DaggerAppComponent
import com.gscube.smsbulker.utils.NetworkUtils
import com.gscube.smsbulker.utils.SecureStorage
import javax.inject.Inject
import com.gscube.smsbulker.utils.SecurityProviderInstaller

class SmsBulkerApplication : Application() {
    
    @Inject
    lateinit var secureStorage: SecureStorage
    
    @Inject
    lateinit var networkUtils: NetworkUtils

    lateinit var appComponent: AppComponent
        private set

    // Inside the onCreate method
    override fun onCreate() {
        super.onCreate()
        
        // Install security provider first - add this line
        SecurityProviderInstaller.installIfNeeded(this)
        
        // Set instance first
        instance = this
        
        // Initialize component and inject
        appComponent = DaggerAppComponent.factory().create(this)
        appComponent.inject(this)
        
        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        
        // Subscribe to delivery reports topic
        FirebaseMessaging.getInstance().subscribeToTopic("delivery_reports")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Subscribed to delivery reports topic")
                } else {
                    Log.e(TAG, "Failed to subscribe to delivery reports topic", task.exception)
                }
            }
    }

    companion object {
        private const val TAG = "SmsBulkerApplication"
        lateinit var instance: SmsBulkerApplication
            private set
    }
}
