package com.motodrive.tft

import android.app.Application
import org.osmdroid.config.Configuration
import java.io.File

class MotoDriveApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize OSMDroid for offline/online motorcycle navigation map
        try {
            val osmConfig = Configuration.getInstance()
            osmConfig.userAgentValue = "TVSRoninTFT/1.0 (Realme C83 Motorcycle Infotainment)"
            val basePath = File(cacheDir, "osmdroid")
            osmConfig.osmdroidBasePath = basePath
            osmConfig.osmdroidTileCache = File(basePath, "tiles")
            osmConfig.load(this, getSharedPreferences("osmdroid_prefs", MODE_PRIVATE))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        lateinit var instance: MotoDriveApp
            private set
    }
}

