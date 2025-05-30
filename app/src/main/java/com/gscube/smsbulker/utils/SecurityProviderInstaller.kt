package com.gscube.smsbulker.utils

import android.content.Context
import android.util.Log
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.security.ProviderInstaller

object SecurityProviderInstaller {
    private const val TAG = "SecurityProvider"

    fun installIfNeeded(context: Context) {
        try {
            ProviderInstaller.installIfNeeded(context)
            Log.d(TAG, "Security provider installed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error installing security provider: ${e.message}")
            // Check if Google Play Services is available
            val availability = GoogleApiAvailability.getInstance()
            val resultCode = availability.isGooglePlayServicesAvailable(context)
            if (resultCode != com.google.android.gms.common.ConnectionResult.SUCCESS) {
                Log.e(TAG, "Google Play Services not available: $resultCode")
                if (availability.isUserResolvableError(resultCode)) {
                    // You could show a dialog here if needed
                    Log.d(TAG, "User resolvable error")
                }
            }
        }
    }
}