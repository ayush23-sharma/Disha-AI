# Disha AI — Women's Safety Navigation Prototype

Disha AI is an Android prototype exploring safety-aware route comparison alongside emergency-contact features. It combines a Kotlin/Jetpack Compose app with a Python/FastAPI backend that applies DBSCAN clustering to **synthetic geographical coordinates for Faridabad**.

> **Prototype limitation:** the current data is synthetic, not verified crime or safety data. The app demonstrates a technical workflow; it does not establish that a location is dangerous or that a route is objectively safe. Do not rely on it for real-world safety decisions.

---

## Features

| Feature | Description |
|---|---|
| **Firebase Authentication** | Sign in using Email/Password or Google through FirebaseUI Auth. |
| **Emergency SOS** | Use the Emergency button or shake the phone three times within the configured detection window. The app uses the last location it retrieved, sends SMS messages to saved guardians, opens an email app with a prefilled message, and attempts to call the first guardian when the required permission is granted. Email is not silently sent in the background. |
| **Guardian Management** | Add, view, and delete trusted contacts stored locally using Room. |
| **Route Comparison Prototype** | Displays Google Maps and evaluates alternative routes returned by the Directions API using configured route-scoring rules. |
| **Danger-Zone Overlay** | Fetches centroids from the FastAPI backend. DBSCAN groups dense regions in synthetic coordinate data; those groups are displayed on the map. |

The current route-scoring rules apply a penalty when a route passes within approximately 500 metres of a flagged danger-zone centroid and a bonus when it passes near a hardcoded safe-haven point. These are configured heuristics, not a trained route-safety prediction model.

---

## Tech Stack

### Android app

- Kotlin, Jetpack Compose, Navigation Compose
- Firebase Authentication and FirebaseUI Auth
- Google Maps SDK for Android
- Google Directions API (currently called directly by the Android app)
- Fused Location Provider
- Room for local guardian storage
- Retrofit 2 and OkHttp for backend communication
- ViewModel, LiveData and Kotlin Coroutines

### Backend

- Python 3.10+
- FastAPI and Uvicorn
- scikit-learn (DBSCAN) and NumPy
- ngrok for temporary development access from the Android device

---

## Prerequisites

- Android Studio with Android SDK 34 installed
- JDK 17 (used by the Gradle build)
- Android device or emulator running API 24 (Android 7.0) or later
- Python 3.10 or later
- An ngrok account and CLI for testing the Android app against the locally running backend
- A Firebase project with Email/Password and Google sign-in enabled
- A Google Cloud project with Maps SDK for Android and Directions API enabled

---

## Setup

### 1. Clone the repository

```bash
git clone https://github.com/ayush23-sharma/Disha-AI.git
cd Disha-AI
```

### 2. Configure `local.properties`

Copy the example file. On Windows PowerShell:

```powershell
Copy-Item .\local.properties.example .\local.properties
```

On macOS/Linux:

```bash
cp local.properties.example local.properties
```

Open `local.properties` and set your local Android SDK path and your own backend URL. For example:

```properties
# Use the SDK path on your machine. Android Studio may populate sdk.dir automatically.
sdk.dir=C:\\Users\\YOUR_USERNAME\\AppData\\Local\\Android\\Sdk

# Set this after starting your backend and ngrok; keep the trailing slash.
BACKEND_BASE_URL=https://YOUR-NGROK-DOMAIN.ngrok-free.app/
```

Use the actual SDK path for your machine. On macOS, a common SDK location is `~/Library/Android/sdk`; Linux installations vary.

`local.properties` is ignored by Git. The Android Gradle build reads `BACKEND_BASE_URL` from this file and exposes it to the app as `BuildConfig.BACKEND_BASE_URL`. The backend URL is configuration, **not authentication**; the app includes the configured URL in the built APK.

> **Legacy template note:** `local.properties.example` currently contains a `MAPS_API_KEY` placeholder, but the current Gradle/app code does not read that property. Configure the Maps key in the XML resources in Step 4 instead. The unused property can be removed from the template in a follow-up cleanup.

### 3. Configure Firebase

1. Open the [Firebase Console](https://console.firebase.google.com/) and select or create a project.
2. Register an Android app with package name `com.shubham.womensafety`.
3. Register the SHA-1 fingerprint for your debug signing certificate. From the project root, run:

   **Windows PowerShell:**
   ```powershell
   .\gradlew.bat signingReport
   ```

   **macOS/Linux:**
   ```bash
   ./gradlew signingReport
   ```

4. Download `google-services.json` for that Android app and place it at:

   ```text
   app/google-services.json
   ```

5. In Firebase Console → Authentication → Sign-in method, enable **Email/Password** and **Google**.

The repository includes `app/google-services.json.example` as a structural example only. It contains placeholders; it is not a working Firebase configuration. Do not put service-account credentials or other private server credentials in the Android app.

### 4. Configure Google Maps

The current prototype reads the `google_maps_key` string resource from Android manifest metadata. Create the resource file for the build variant you intend to run.

**Debug build — Windows PowerShell:**

```powershell
New-Item -ItemType Directory -Force .\app\src\debug\res\values | Out-Null
Copy-Item .\google_maps_api.debug.xml.example .\app\src\debug\res\values\google_maps_api.xml
```

**Debug build — macOS/Linux:**

```bash
mkdir -p app/src/debug/res/values
cp google_maps_api.debug.xml.example app/src/debug/res/values/google_maps_api.xml
```

Open the copied file and replace `YOUR_DEBUG_GOOGLE_MAPS_API_KEY` with your own development key.

For a release build, create `app/src/release/res/values/` and copy `google_maps_api.release.xml.example` to `app/src/release/res/values/google_maps_api.xml`. Then replace `YOUR_RELEASE_GOOGLE_MAPS_API_KEY` with the value for that build. Apply the same directory-creation step for Windows or macOS/Linux as shown above, substituting `release` for `debug`.

The actual `google_maps_api.xml` files are ignored by Git; only the `.example` templates belong in the public repository.

#### Important API-key security limitation

In the current code, **the same key is used for Android Maps SDK rendering and for a Directions web-service request made directly from the Android app**. A key packaged in an Android app can be extracted, so it must not be treated as a secret. Applying an Android-app application restriction to this shared key may cause the client-side Directions request to fail.

For a local/demo build:

- Use a dedicated development key, not a key with access to unrelated or production services.
- Restrict the key to only the APIs this prototype needs where compatible with the current implementation.
- Set sensible quotas and billing alerts in Google Cloud Console.
- Never commit the real key or share it in screenshots, logs, or issue reports.

**This is not a production-secure key architecture.** Before production use, refactor Directions requests to go through the backend using a server-side key, while using a separate Android Maps SDK key restricted to the package name and signing-certificate SHA-1. The current code has not implemented that separation yet. Also, the current Directions error logging can include the request URL (and therefore the key); redact the key from logs before wider deployment.

Google Cloud Console: [APIs & Services → Credentials](https://console.cloud.google.com/apis/credentials).

### 5. Run the Python backend

The backend generates synthetic Faridabad coordinate data, applies DBSCAN, and returns the resulting centroids.

Create and activate a virtual environment from the project root.

**Windows PowerShell:**

```powershell
py -3.10 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
pip install fastapi "uvicorn[standard]" scikit-learn numpy pydantic
```

**macOS/Linux:**

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
pip install fastapi "uvicorn[standard]" scikit-learn numpy pydantic
```

Start the backend:

```bash
python main.py
```

The API runs on port `8000`. With the server running, verify locally at:

- Health check: `http://127.0.0.1:8000/health`
- API documentation: `http://127.0.0.1:8000/docs`
- Danger-zone endpoint: `http://127.0.0.1:8000/api/v1/danger-zones`

#### Expose the backend through ngrok (development only)

Install ngrok separately and authenticate its CLI according to the [official ngrok setup guide](https://ngrok.com/docs/getting-started/).

In a **separate terminal**, run:

```bash
ngrok http 8000
```

If `ngrok.exe` is in the current Windows directory rather than on your PATH, run `./ngrok.exe http 8000` in PowerShell instead.

Copy the HTTPS forwarding URL displayed by ngrok and put it in `local.properties`, with a trailing slash. Example placeholder:

```properties
BACKEND_BASE_URL=https://YOUR-NGROK-DOMAIN.ngrok-free.app/
```

Keep the ngrok process running while testing the app. Temporary ngrok URLs may change when the tunnel restarts; if the URL changes, update `BACKEND_BASE_URL` and rebuild the app. The ngrok URL is not a security mechanism. The backend is exposed to the internet while the tunnel is active, so use this setup only for development and do not send sensitive data through it.

### 6. Build and run the Android app

Open the cloned folder in Android Studio, allow Gradle sync to finish, complete Firebase and Maps configuration, and run the `app` configuration on a device or emulator (API 24+).

From PowerShell on Windows:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

From macOS/Linux:

```bash
./gradlew assembleDebug
./gradlew installDebug
```

The app needs internet access, location permission, SMS permission, and call permission for the corresponding functions. SOS behaviour depends on permissions, device support, and the availability of a recent location.

---

## API Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/danger-zones` | Returns DBSCAN-derived danger-zone centroids generated from synthetic coordinates |
| `GET` | `/health` | Backend health check |
| — | `/docs` | FastAPI's interactive API documentation |

---

## Project Structure

```text
Disha-AI/
├── app/
│   ├── build.gradle
│   ├── google-services.json.example   # Firebase config template
│   └── src/
│       ├── main/
│       │   ├── java/com/shubham/womensafety/
│       │   │   ├── FirebaseAuth/      # Authentication state and login ViewModel
│       │   │   ├── database/          # Room guardian entity, DAO and database
│       │   │   ├── guardiandetail/    # Guardian management ViewModel
│       │   │   ├── network/           # Retrofit client, API and response models
│       │   │   ├── repository/        # Danger-zone data repository
│       │   │   ├── ui/
│       │   │   │   ├── components/    # Reusable Compose components
│       │   │   │   ├── navigation/    # Navigation graph and routes
│       │   │   │   └── screens/       # Dashboard, guardian and map screens
│       │   │   ├── utils/             # Permission utilities
│       │   │   └── MainActivity.kt
│       │   └── test/ and androidTest/ # Unit and instrumentation tests
│       ├── debug/res/values/          # Local Maps key resource (ignored)
│       └── release/res/values/        # Local Maps key resource (ignored)
├── gradle/wrapper/                    # Gradle wrapper
├── main.py                            # FastAPI application
├── ml_clustering.py                   # DBSCAN danger-zone generation
├── local.properties                   # Local SDK/backend config (ignored)
├── local.properties.example           # Configuration template
├── google_maps_api.debug.xml.example  # Debug Maps resource template
└── google_maps_api.release.xml.example # Release Maps resource template
```

---

## Android Permissions

| Permission | Purpose |
|---|---|
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Location for the map and SOS message |
| `SEND_SMS` | Sends an emergency SMS to saved guardians |
| `CALL_PHONE` | Initiates a call to the first guardian when allowed |
| `INTERNET` | Maps, Firebase, Directions requests and backend API communication |

The app may open the device's email app with a prefilled message during the SOS flow; email delivery depends on the user and email application.

---

## Security and Data Limitations

- Never commit `local.properties`, `google-services.json`, actual `google_maps_api.xml` files, service-account credentials, signing keys or other private configuration.
- The `.example` files contain placeholders and document the expected structure; they are not working credentials.
- Firebase client configuration is not equivalent to server credentials. Configure Firebase Authentication appropriately and apply suitable Firebase security controls for any Firebase services you use.
- The Directions API is currently called from the Android client with a key that is also used for the Android Maps SDK. This key can be extracted from the app, and current error logs may include its request URL. This is a known limitation to address before production use.
- The backend currently generates synthetic coordinates. Danger zones are demonstrations of DBSCAN clustering and must not be interpreted as verified crime hotspots or reliable safety advice.
- The backend is exposed through a development ngrok tunnel rather than a permanent production deployment.

---

## Future Improvements

- Replace synthetic coordinates with credible, appropriately sourced and maintained safety data.
- Validate route scoring against defined criteria and realistic test cases.
- Move Directions web-service requests to the backend and keep the server-side API key out of the Android app.
- Redact API keys from logs and strengthen API-key restrictions, quotas and billing safeguards.
- Deploy the backend securely and evaluate privacy, security, usability and reliability through a controlled pilot.

---

## Disclaimer

Disha AI is an academic prototype. It has not been validated to identify actual dangerous areas or guarantee safer routes. Do not use it as a substitute for official emergency services or verified local safety information.
