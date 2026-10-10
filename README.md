# Disha AI — Women Safety App

An Android application that helps women stay safe through real-time safe-route navigation, an emergency SOS system, and an AI-powered danger-zone overlay backed by a Python/FastAPI service.

---

## Features

| Feature | Description |
|---|---|
| **Firebase Authentication** | Sign-in with Email/Password or Google account via FirebaseUI |
| **Emergency SOS** | One-tap (or shake-to-trigger) button that sends your live GPS location via SMS and email to all registered guardians and auto-dials the first guardian |
| **Guardian Management** | Add, view, and delete trusted contacts stored locally with Room |
| **Safe Navigation** | Google Maps view with danger-zone overlay and Directions API route scoring — routes are ranked by proximity to danger zones and safe havens |
| **Danger Zone Overlay** | Fetched from the FastAPI backend, which uses DBSCAN clustering on synthetic crime-coordinate data for the Faridabad region |

---

## Tech Stack

**Android app**
- Kotlin · Jetpack Compose · Navigation Compose
- Firebase Authentication · FirebaseUI Auth
- Google Maps SDK for Android · Directions API (web service)
- Fused Location Provider
- Room (local guardian storage)
- Retrofit 2 · OkHttp (backend communication)
- MVVM (ViewModel · LiveData · Coroutines)

**Backend**
- Python 3 · FastAPI · Uvicorn
- scikit-learn (DBSCAN) · NumPy
- Exposed via ngrok during development

---

## Prerequisites

- Android Studio Hedgehog or later
- Android SDK 34 (compileSdkVersion)
- Min Android API 24 (Android 7.0)
- Python 3.9+
- [ngrok](https://ngrok.com/) account and CLI (free tier is fine)
- A Firebase project with Authentication enabled
- A Google Cloud project with **Maps SDK for Android** and **Directions API** enabled

---

## Setup

### 1. Clone the repository

```bash
git clone https://github.com/YOUR_USERNAME/Disha-AI.git
cd Disha-AI
```

### 2. Configure `local.properties`

Copy the example file and fill in your values:

```bash
cp local.properties.example local.properties
```

Then edit `local.properties`:

```properties
sdk.dir=/path/to/your/Android/Sdk          # your local Android SDK path
MAPS_API_KEY=YOUR_GOOGLE_MAPS_API_KEY      # see Step 4
BACKEND_BASE_URL=YOUR_BACKEND_BASE_URL     # see Step 5 — include trailing slash
```

> `local.properties` is gitignored and never committed.

---

### 3. Configure Firebase (`google-services.json`)

1. Go to [Firebase Console](https://console.firebase.google.com) and open (or create) your project.
2. Add an Android app with package name `com.shubham.womensafety`.
3. Register your debug SHA-1 fingerprint (get it by running `./gradlew signingReport` and copying the SHA-1 for the `debug` variant).
4. Download `google-services.json` and place it at:
   ```
   app/google-services.json
   ```
5. In Firebase Console → Authentication → Sign-in method, enable **Email/Password** and **Google**.

> See `app/google-services.json.example` for the expected file structure.

---

### 4. Configure Google Maps API keys

> **⚠️ Dual-use key warning:** The same key is used for both the Android Maps SDK (map tile rendering) and the Directions web-service API (HTTP call). If you restrict this key to *Android apps only* in Google Cloud Console, the Directions call will fail with `REQUEST_DENIED`.
>
> **Option A (development):** Leave the key unrestricted.
> **Option B (production):** Create two separate keys — one restricted to Android app (package + SHA-1) for the Maps SDK, and a second unrestricted/IP-restricted key for the Directions web service. Pass the second key separately via `BuildConfig` and update `MapsViewModel`.

**Steps:**
1. Go to [Google Cloud Console](https://console.cloud.google.com) → APIs & Services → Credentials.
2. Create an API key (or reuse one). Enable **Maps SDK for Android** and **Directions API** on your project.
3. Copy the key into **both** of the following files (copy from the `.example` templates):

**Debug build:**
```bash
cp google_maps_api.debug.xml.example app/src/debug/res/values/google_maps_api.xml
# Replace YOUR_DEBUG_GOOGLE_MAPS_API_KEY with your real key
```

**Release build:**
```bash
cp google_maps_api.release.xml.example app/src/release/res/values/google_maps_api.xml
# Replace YOUR_RELEASE_GOOGLE_MAPS_API_KEY with your real key
# For release, get your keystore SHA-1 with:
# keytool -list -v -keystore your-release-key.jks -alias your-alias
```

> The same key value can be used for both debug and release during development.

---

### 5. Run the Python backend

The Android app fetches danger-zone data from a local FastAPI server exposed over ngrok.

**Install dependencies:**
```bash
pip install fastapi "uvicorn[standard]" scikit-learn numpy pydantic
```

**Start the server:**
```bash
python main.py
```
The server starts on `http://localhost:8000`. You can verify it at `http://localhost:8000/health`.

**Expose it with ngrok:**
```bash
# Windows
.\ngrok http 8000

# macOS / Linux
ngrok http 8000
```

Copy the HTTPS forwarding URL from ngrok output (e.g. `https://xxxx-xx-xx-xxx-xx.ngrok-free.app`) and set it in `local.properties`:
```properties
BACKEND_BASE_URL=https://xxxx-xx-xx-xxx-xx.ngrok-free.app/
```

> Include the trailing `/`. The URL changes every time ngrok restarts unless you have a reserved domain on a paid plan. Rebuild the app after updating the URL.

**API endpoints:**
| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/danger-zones` | Returns DBSCAN-derived danger-zone centroids |
| `GET` | `/health` | Health check |
| — | `/docs` | Auto-generated Swagger UI |

---

### 6. Build and run

Open the project in Android Studio and run the `app` configuration on a device or emulator (API 24+).

Or build from the command line:
```bash
# Debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

---

## Project Structure

```
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/shubham/womensafety/
│   │   │   │   ├── database/          # Room — Guardian entity, DAO, Database
│   │   │   │   ├── FirebaseAuth/      # FirebaseUserLiveData, LoginViewModel
│   │   │   │   ├── network/           # Retrofit client, DangerZoneApi, response models
│   │   │   │   ├── repository/        # SafetyRepository (fetches danger zones)
│   │   │   │   ├── ui/
│   │   │   │   │   ├── screens/       # DashboardScreen, MapsScreen, GuardianInfoScreen, AddGuardianScreen
│   │   │   │   │   ├── components/    # Reusable Composables
│   │   │   │   │   ├── navigation/    # AppNavGraph, Routes
│   │   │   │   │   └── theme/         # Color, Theme
│   │   │   │   └── MainActivity.kt
│   │   ├── debug/res/values/          # google_maps_api.xml (gitignored — copy from .example)
│   │   └── release/res/values/        # google_maps_api.xml (gitignored — copy from .example)
│   ├── google-services.json           # gitignored — copy from google-services.json.example
│   └── build.gradle
├── main.py                            # FastAPI server entry point
├── ml_clustering.py                   # DBSCAN danger-zone generation
├── local.properties                   # gitignored — copy from local.properties.example
├── local.properties.example           # template — commit this, not local.properties
├── google-services.json.example       # Firebase config template
├── google_maps_api.debug.xml.example  # Debug Maps key template
└── google_maps_api.release.xml.example # Release Maps key template
```

---

## Permissions

| Permission | Used for |
|---|---|
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | GPS coordinates for SOS message and map display |
| `SEND_SMS` | Sending emergency SMS to guardians |
| `CALL_PHONE` | Auto-dialling the first guardian during SOS |
| `INTERNET` | Maps, Directions API, Firebase, backend API |

---

## Security Notes

- All API keys and secrets are stored in `local.properties` (gitignored) or gitignored resource files — they are never committed.
- See the `.example` template files at the project root for the expected format of each secret file.
- The Google Maps key is used for both the Android Maps SDK and Directions web-service calls. Applying an Android-app restriction silently breaks route calculation — see Step 4 for options.
