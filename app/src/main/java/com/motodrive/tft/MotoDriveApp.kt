package com.motodrive.tft

import android.app.Application
import com.google.android.libraries.places.api.Places

class MotoDriveApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize Google Places SDK with API key from BuildConfig (injected via Secrets Gradle Plugin)
        try {
            val apiKey = BuildConfig.MAPS_API_KEY
            if (apiKey.isNotBlank() && !Places.isInitialized()) {
                Places.initialize(applicationContext, apiKey)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        lateinit var instance: MotoDriveApp
            private set
    }
}
