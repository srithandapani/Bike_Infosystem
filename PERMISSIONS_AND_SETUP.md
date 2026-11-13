# MOTODRIVE TFT – Permissions & Setup Documentation

## Android Manifest Permissions

MOTODRIVE TFT adheres strictly to Android security and privacy guidelines. It does not bypass any operating system access controls or require root privileges.

### 1. Location Permissions
```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```
- **Purpose**: Powers real-time GPS speed, accumulated trip distance, compass heading orientation, and navigation positioning.
- **Rider Prompt**: Select **"While using the app"** and enable **"Use precise location"**.

### 2. Audio Permission (Voice Assistant)
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```
- **Purpose**: Required by Android SpeechRecognizer for hands-free voice commands.
- **Rider Prompt**: Select **"While using the app"**.

### 3. Bluetooth Permissions
```xml
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
```
- **Purpose**: Connects to motorcycle helmet headsets (Cardo, Sena, Bluetooth helmets), discovers audio devices, and powers automatic dashboard launch.

### 4. Telephony Permission (Hands-Free Phone Calls)
```xml
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
```
- **Purpose**: Detects incoming call ringing states to show glanceable cockpit call alerts without rider distraction.

### 5. Media Notification Access (Media Session)
- **Setting Path**: `Settings -> Apps -> Special app access -> Notification access -> MOTODRIVE TFT -> Allow`.
- **Purpose**: Enables MOTODRIVE TFT to read the track title and artist from Spotify, YouTube Music, or other audio players and deliver play/pause/skip commands directly from the motorcycle screen.

### 6. Screen Wake Lock
```xml
<uses-permission android:name="android.permission.WAKE_LOCK" />
```
- **Purpose**: Keeps display illuminated while mounted on the motorcycle handlebar without timing out.

