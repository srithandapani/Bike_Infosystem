# MOTODRIVE TFT – Installation Instructions for Realme C83

## Option A: Direct Installation via ADB (Fastest)

1. Connect the Realme C83 to your computer via USB.
2. On the Realme C83:
   - Go to **Settings** > **About Device** > **Version** > Tap **Build Number** 7 times to enable Developer Options.
   - Go to **Settings** > **System Settings** > **Developer Options** > Enable **USB Debugging**.
   - When prompted on the phone, check **Always allow from this computer** and tap **Allow**.
3. Open a terminal in `C:\Bike_Infosystem`:
   - To install the **Debug APK**:
     ```cmd
     adb install -r app\build\outputs\apk\debug\app-debug.apk
     ```
   - To install the **Release APK**:
     ```cmd
     adb install -r app\build\outputs\apk\release\app-release.apk
     ```
4. Launch the application:
   ```cmd
   adb shell am start -n com.motodrive.tft/.MainActivity
   ```

---

## Option B: Standalone Installation via Phone Storage

1. Transfer either APK file to your Realme C83:
   - **Debug APK**: `C:\Bike_Infosystem\app\build\outputs\apk\debug\app-debug.apk`
   - **Release APK**: `C:\Bike_Infosystem\app\build\outputs\apk\release\app-release.apk`
2. Open the **Files / File Manager** app on the Realme C83.
3. Locate `app-release.apk` (or `app-debug.apk`) in Downloads or Internal Storage.
4. Tap the APK to install. (If prompted, allow **"Install unknown apps"** for File Manager).
5. Open **MOTODRIVE TFT** from your app drawer.
6. Mount phone horizontally on motorcycle handlebar mount and enjoy the M Sport experience!

