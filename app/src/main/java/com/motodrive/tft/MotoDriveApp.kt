package com.motodrive.tft

import android.app.Application
import android.content.Context
import com.google.android.libraries.places.api.Places
import org.osmdroid.config.Configuration

class MotoDriveApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 1. Initialize Osmdroid Map Engine Configuration
        try {
            Configuration.getInstance().userAgentValue = "MotoDriveTFT/1.0 (TVS Ronin Infotainment)"
            Configuration.getInstance().load(this, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Initialize Google Places SDK with API key from BuildConfig (if valid)
        try {
            val apiKey = BuildConfig.MAPS_API_KEY
            if (apiKey.isNotBlank() && !apiKey.contains("Placeholder") && !Places.isInitialized()) {
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
