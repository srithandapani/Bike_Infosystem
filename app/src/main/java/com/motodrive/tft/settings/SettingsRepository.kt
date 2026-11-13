package com.motodrive.tft.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "motodrive_settings")

data class AppSettings(
    val speedUnit: String = "km/h",
    val temperatureUnit: String = "°C",
    val is24HourClock: Boolean = false,
    val keepScreenOn: Boolean = true,
    val bluetoothAutoLaunch: Boolean = true,
    val highFrequencyGps: Boolean = true,
    val clusterStyle: String = "DIGITAL", // "DIGITAL", "ANALOG", "HYBRID"
    val themeStyle: String = "TVS_RONIN_PURPLE", // "TVS_RONIN_PURPLE", "M_SPORT_BLUE"
    val iphoneNotificationsEnabled: Boolean = true,
    val showNotificationPreviews: Boolean = true,
    val notifyIncomingCalls: Boolean = true,
    val notifyWhatsApp: Boolean = true,
    val notifyInstagram: Boolean = true,
    val notifySms: Boolean = true,
    val notifySnapchat: Boolean = true,
    val homeLat: Double? = null,
    val homeLon: Double? = null,
    val homeName: String = "",
    val workLat: Double? = null,
    val workLon: Double? = null,
    val workName: String = ""
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val SPEED_UNIT = stringPreferencesKey("speed_unit")
        val TEMP_UNIT = stringPreferencesKey("temp_unit")
        val IS_24_HOUR = booleanPreferencesKey("is_24_hour")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val BT_AUTO_LAUNCH = booleanPreferencesKey("bt_auto_launch")
        val HIGH_FREQ_GPS = booleanPreferencesKey("high_freq_gps")
        val CLUSTER_STYLE = stringPreferencesKey("cluster_style")
        val THEME_STYLE = stringPreferencesKey("theme_style")
        val IPHONE_NOTIFS_ENABLED = booleanPreferencesKey("iphone_notifs_enabled")
        val SHOW_NOTIF_PREVIEWS = booleanPreferencesKey("show_notif_previews")
        val NOTIFY_CALLS = booleanPreferencesKey("notify_calls")
        val NOTIFY_WHATSAPP = booleanPreferencesKey("notify_whatsapp")
        val NOTIFY_INSTAGRAM = booleanPreferencesKey("notify_instagram")
        val NOTIFY_SMS = booleanPreferencesKey("notify_sms")
        val NOTIFY_SNAPCHAT = booleanPreferencesKey("notify_snapchat")
        val HOME_LAT = doublePreferencesKey("home_lat")
        val HOME_LON = doublePreferencesKey("home_lon")
        val HOME_NAME = stringPreferencesKey("home_name")
        val WORK_LAT = doublePreferencesKey("work_lat")
        val WORK_LON = doublePreferencesKey("work_lon")
        val WORK_NAME = stringPreferencesKey("work_name")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            speedUnit = prefs[Keys.SPEED_UNIT] ?: "km/h",
            temperatureUnit = prefs[Keys.TEMP_UNIT] ?: "°C",
            is24HourClock = prefs[Keys.IS_24_HOUR] ?: false,
            keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: true,
            bluetoothAutoLaunch = prefs[Keys.BT_AUTO_LAUNCH] ?: true,
            highFrequencyGps = prefs[Keys.HIGH_FREQ_GPS] ?: true,
            clusterStyle = prefs[Keys.CLUSTER_STYLE] ?: "DIGITAL",
            themeStyle = prefs[Keys.THEME_STYLE] ?: "TVS_RONIN_PURPLE",
            iphoneNotificationsEnabled = prefs[Keys.IPHONE_NOTIFS_ENABLED] ?: true,
            showNotificationPreviews = prefs[Keys.SHOW_NOTIF_PREVIEWS] ?: true,
            notifyIncomingCalls = prefs[Keys.NOTIFY_CALLS] ?: true,
            notifyWhatsApp = prefs[Keys.NOTIFY_WHATSAPP] ?: true,
            notifyInstagram = prefs[Keys.NOTIFY_INSTAGRAM] ?: true,
            notifySms = prefs[Keys.NOTIFY_SMS] ?: true,
            notifySnapchat = prefs[Keys.NOTIFY_SNAPCHAT] ?: true,
            homeLat = prefs[Keys.HOME_LAT],
            homeLon = prefs[Keys.HOME_LON],
            homeName = prefs[Keys.HOME_NAME] ?: "",
            workLat = prefs[Keys.WORK_LAT],
            workLon = prefs[Keys.WORK_LON],
            workName = prefs[Keys.WORK_NAME] ?: ""
        )
    }

    suspend fun setSpeedUnit(unit: String) {
        context.dataStore.edit { it[Keys.SPEED_UNIT] = unit }
    }

    suspend fun setTemperatureUnit(unit: String) {
        context.dataStore.edit { it[Keys.TEMP_UNIT] = unit }
    }

    suspend fun set24HourClock(enabled: Boolean) {
        context.dataStore.edit { it[Keys.IS_24_HOUR] = enabled }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { it[Keys.KEEP_SCREEN_ON] = enabled }
    }

    suspend fun setBluetoothAutoLaunch(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BT_AUTO_LAUNCH] = enabled }
    }

    suspend fun setHighFrequencyGps(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HIGH_FREQ_GPS] = enabled }
    }

    suspend fun setClusterStyle(style: String) {
        context.dataStore.edit { it[Keys.CLUSTER_STYLE] = style }
    }

    suspend fun setThemeStyle(theme: String) {
        context.dataStore.edit { it[Keys.THEME_STYLE] = theme }
    }

    suspend fun setIphoneNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.IPHONE_NOTIFS_ENABLED] = enabled }
    }

    suspend fun setShowNotificationPreviews(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_NOTIF_PREVIEWS] = enabled }
    }

    suspend fun setNotifyIncomingCalls(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_CALLS] = enabled }
    }

    suspend fun setNotifyWhatsApp(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_WHATSAPP] = enabled }
    }

    suspend fun setNotifyInstagram(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_INSTAGRAM] = enabled }
    }

    suspend fun setNotifySms(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_SMS] = enabled }
    }

    suspend fun setNotifySnapchat(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_SNAPCHAT] = enabled }
    }

    suspend fun saveLocation(type: String, lat: Double, lon: Double, name: String) {
        context.dataStore.edit { prefs ->
            if (type.equals("home", ignoreCase = true)) {
                prefs[Keys.HOME_LAT] = lat
                prefs[Keys.HOME_LON] = lon
                prefs[Keys.HOME_NAME] = name
            } else if (type.equals("work", ignoreCase = true)) {
                prefs[Keys.WORK_LAT] = lat
                prefs[Keys.WORK_LON] = lon
                prefs[Keys.WORK_NAME] = name
            }
        }
    }
}

