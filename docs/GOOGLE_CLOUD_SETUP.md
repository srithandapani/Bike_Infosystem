# Google Cloud Platform Setup Guide
### TVS Ronin 225 Infotainment – Google Navigation & Places SDK

This guide explains how to provision, configure, and secure your Google Maps Platform credentials for the **Bike ODO Dynamic System** (`com.motodrive.tft`).

---

## 1. Google Cloud Project & Required APIs

1. Open the [Google Cloud Console](https://console.cloud.google.com/).
2. Create a new project or select an existing one (e.g. `tvs-ronin-infotainment`).
3. Ensure a billing account is linked to your project. Google provides a recurring **$200 monthly free tier credit** for Maps Platform.
4. Navigate to **APIs & Services > Library** and enable the following APIs:
   - **Navigation SDK for Android** *(Primary in-app turn-by-turn guidance engine)*
   - **Places SDK for Android** / **Places API (New)** *(Real-time search, Autocomplete, Place Details, Petrol Pumps & Motorcycle Repair nearby search)*
   - **Maps SDK for Android** *(Base vector tiles & map rendering)*
   - **Routes API** *(Two-Wheeler routing fallback engine)*

> **Note on Two-Wheeler Routing**: Google Navigation SDK natively supports two-wheeler routing mode (`RoutingOptions.TravelMode.TWO_WHEELER`), taking advantage of specialized motorcycle paths, avoiding highways where two-wheelers are prohibited in India, and providing accurate motorcycle ETAs based on local traffic.

---

## 2. Generate and Restrict Your API Key

To ensure your API key remains secure and cannot be abused by third parties, apply both **Application restrictions** and **API restrictions**:

### Step 2.1: Create API Key
1. In Google Cloud Console, go to **APIs & Services > Credentials**.
2. Click **+ Create Credentials > API Key**.
3. Copy the generated key.

### Step 2.2: Apply Application Restrictions (Android App)
1. Select **Android apps**.
2. Click **+ Add an item**:
   - **Package name**: `com.motodrive.tft`
   - **SHA-1 certificate fingerprint**:
     To find your local debug SHA-1, execute:
     ```bash
     keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
     ```
     Copy the `SHA1` fingerprint (e.g. `AA:BB:CC:...`) and paste it into the console.
     *(For release builds, also add the release keystore SHA-1 fingerprint).*

### Step 2.3: Apply API Restrictions
Select **Restrict key** and check:
- *Navigation SDK for Android*
- *Places SDK for Android*
- *Maps SDK for Android*
- *Routes API*

---

## 3. Configure Your Local Project

The project uses the **Secrets Gradle Plugin** (`com.google.android.libraries.mapsplatform.secrets-gradle-plugin`) to inject your API key safely at compile time without committing it to git.

1. Open (or create) `local.properties` in the project root:
   ```properties
   sdk.dir=C\:\\Users\\PrasaD\\AppData\\Local\\Android\\Sdk
   MAPS_API_KEY=AIzaSyYourActualGoogleMapsApiKeyHere
   ```
2. `local.properties` is already listed in `.gitignore` and will never be tracked or pushed to remote repositories.
3. The key is automatically injected into `AndroidManifest.xml` (`com.google.android.geo.API_KEY`) and `BuildConfig.MAPS_API_KEY`.

---

## 4. Navigation SDK Terms of Service & Initialization Flow

Google Navigation SDK requires user acceptance of terms on first launch:
1. `NavigationApi.getNavigator(activity, customRoutesListener)` handles the initialization.
2. If terms have not been accepted, `NavigationApi.showTermsAndConditionsDialog()` is invoked automatically.
3. Upon acceptance, the embedded `SupportNavigationFragment` is attached, forced to AMOLED night mode (`ForceNightMode.FORCE_NIGHT`), and navigation starts in landscape motorcycle perspective.
4. In case the device is offline or the Navigation SDK license is not provisioned for the project, the app seamlessly falls back to:
   - **Fallback B**: Routes API with `TWO_WHEELER` mode.
   - **Fallback C**: Google Maps direct navigation intent (`google.navigation:q=...&mode=l`).

---

## 5. Foreground Service & Battery Optimization

- The navigation service runs as a foreground service (`GoogleNavigationService`) with type `location`.
- It broadcasts live turn-by-turn guidance notifications so directions remain active even if the app is momentarily backgrounded.
- For maximum GPS stability on Realme / ColorOS devices, set the app battery usage in device Settings to **Unrestricted**.
