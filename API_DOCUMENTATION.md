# MOTODRIVE TFT – API & Integration Documentation

## 1. Google Maps Navigation API & Intents

MOTODRIVE TFT uses supported Android Intent URIs for Google Maps Turn-by-Turn motorcycle navigation.

### Supported Intents:
- **Turn-by-Turn Navigation Intent**:
  ```kotlin
  val gmmIntentUri = Uri.parse("google.navigation:q=" + Uri.encode(destinationQuery) + "&mode=d")
  val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
      setPackage("com.google.android.apps.maps")
      flags = Intent.FLAG_ACTIVITY_NEW_TASK
  }
  ```
- **Fallback Geo Intent**:
  ```kotlin
  val geoUri = Uri.parse("geo:0,0?q=" + Uri.encode(destinationQuery))
  ```

### Optional Google Maps SDK API Key:
If you wish to replace the embedded OpenStreetMap view with Google Maps SDK map tiles, add your Google Maps API key in `AndroidManifest.xml`:
```xml
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="YOUR_GOOGLE_MAPS_API_KEY_HERE" />
```

---

## 2. Weather API (Open-Meteo REST API)

MOTODRIVE TFT connects to Open-Meteo REST API, which is completely open, free for commercial and non-commercial use, and requires **no API keys**.

- **Endpoint**:
  `https://api.open-meteo.com/v1/forecast`
- **Parameters**:
  - `latitude`: GPS latitude from LocationManager
  - `longitude`: GPS longitude from LocationManager
  - `current`: `temperature_2m,relative_humidity_2m,is_day,precipitation,weather_code,wind_speed_10m`
- **WMO Weather Code Parsing**:
  - `0`: Clear Sky
  - `1, 2, 3`: Mainly Clear / Partly Cloudy
  - `45, 48`: Foggy Riding Conditions
  - `51, 53, 55`: Light Drizzle
  - `61, 63, 65`: Rain / Wet Roads Caution
  - `95, 96, 99`: Thunderstorm Warning

---

## 3. MediaSession & Audio Control APIs

### Android NotificationListenerService:
`MotoMediaNotificationListenerService` extends `NotificationListenerService` and queries `MediaSessionManager.getActiveSessions()`:
- Reads `MediaMetadata.METADATA_KEY_TITLE`
- Reads `MediaMetadata.METADATA_KEY_ARTIST`
- Reads `MediaMetadata.METADATA_KEY_DURATION`
- Controls active playback via `MediaController.transportControls` (`play()`, `pause()`, `skipToNext()`, `skipToPrevious()`, `seekTo()`)

### Hardware Key Event Fallback:
When no active token is bound, `AudioManager.dispatchMediaKeyEvent()` dispatches:
- `KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE`
- `KeyEvent.KEYCODE_MEDIA_NEXT`
- `KeyEvent.KEYCODE_MEDIA_PREVIOUS`

---

## 4. Android SpeechRecognizer API (MOTODRIVE VOICE)

- **Engine**: `android.speech.SpeechRecognizer`
- **Intent**: `RecognizerIntent.ACTION_RECOGNIZE_SPEECH`
- **Model**: `RecognizerIntent.LANGUAGE_MODEL_FREE_FORM`
- **Feedback Engine**: `android.speech.tts.TextToSpeech` (audible voice responses in rider's helmet headset)

---

## 5. Preferences DataStore Schema

All settings are stored asynchronously using Jetpack DataStore Preferences (`motodrive_settings.preferences_pb`):
- `speed_unit`: String (`"km/h"` or `"mph"`)
- `temp_unit`: String (`"°C"` or `"°F"`)
- `is_24_hour`: Boolean
- `keep_screen_on`: Boolean
- `bt_auto_launch`: Boolean
- `high_freq_gps`: Boolean

