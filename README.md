# NSBOOSTBD - Android WebView Application

**Professional, production-ready Android WebView app for the NSBOOSTBD SMM Panel.**

- **Website**: https://nsboostbd.com/
- **Package**: `com.nsboostbd.app`
- **Version**: 1.0.0 (versionCode 1)
- **Min SDK**: 23 (Android 6.0)
- **Target / Compile SDK**: 35
- **Language**: Kotlin
- **Build System**: Android Gradle Plugin 8.7.3 + Gradle 8.9 + Java 17

This is a **lifetime free** application for end users. There is **no trial**, **no expiry**, **no subscription**, and **no license check** inside the app. Customers can download and use it indefinitely.

> **Note**: Website hosting, domain renewal, and server costs are separate from this Android application and remain the responsibility of the website owner.

---

## Features

- Full JavaScript + DOM Storage support
- Cookie & session persistence (login stays active)
- HTTPS only (cleartext traffic disabled)
- Loading progress bar
- Pull-to-refresh
- Android back-button history navigation
- Network error screen with Retry button
- File upload support (via system file chooser)
- File download support (opens external viewer/downloader)
- External links open in the device default browser
- Clean white + dark-blue professional UI
- Splash screen
- Responsive WebView experience
- No unnecessary permissions
- Ready for future FCM push notifications (not implemented in v1)

---

## Project Structure

```
NSBOOSTBD/
├── .github/workflows/build-apk.yml   # GitHub Actions automatic APK build
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/nsboostbd/app/MainActivity.kt
│       └── res/
│           ├── drawable/
│           ├── layout/activity_main.xml
│           ├── mipmap-*/ (launcher icons)
│           ├── values/ (colors, strings, themes)
│           └── xml/
├── build.gradle.kts
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
├── settings.gradle.kts
├── .gitignore
└── README.md
```

---

## How to Build Locally (Android Studio)

1. Install **Android Studio** (latest stable recommended).
2. Open the project folder `NSBOOSTBD`.
3. Let Gradle sync (it will download dependencies).
4. Connect a device or start an emulator (API 23+).
5. Click **Run** ▶ or build APK via **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

### Command line (with Gradle Wrapper)

If you generate the wrapper once:

```bash
# On a machine with Gradle installed
gradle wrapper --gradle-version 8.9

# Then
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

---

## GitHub Actions – Automatic APK Build

The workflow `.github/workflows/build-apk.yml`:

- Triggers on push / PR to `main` or `master`
- Supports manual run (`workflow_dispatch`)
- Uses Java 17 + Gradle 8.9
- Builds a **debug APK**
- Uploads it as a downloadable artifact named `NSBOOSTBD-debug-apk`

### How to use

1. Create a new GitHub repository.
2. Upload / push this entire project.
3. Go to the **Actions** tab.
4. Run the workflow (or push a commit).
5. Download the APK from the workflow run artifacts.

This works within GitHub’s free Actions minutes.

---

## Permanent Signing Key (Release APK)

**Never commit the keystore or passwords to Git.**

### 1. Generate a permanent keystore (do this once)

```bash
keytool -genkey -v -keystore nsboostbd-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias nsboostbd
```

- Choose a strong password for the keystore and the key.
- Store the `.jks` file in a **secure offline location** (encrypted drive, password manager, etc.).
- Keep the same keystore for **every future update**. Changing the key means users cannot update the app; they must uninstall and reinstall.

### 2. Create `keystore.properties` (local only, already in .gitignore)

```properties
storeFile=../nsboostbd-release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=nsboostbd
keyPassword=YOUR_KEY_PASSWORD
```

### 3. Configure signing in `app/build.gradle.kts` (add inside `android { }`)

```kotlin
signingConfigs {
    create("release") {
        val keystorePropertiesFile = rootProject.file("keystore.properties")
        if (keystorePropertiesFile.exists()) {
            val keystoreProperties = java.util.Properties()
            keystoreProperties.load(java.io.FileInputStream(keystorePropertiesFile))
            storeFile = file(keystoreProperties["storeFile"] as String)
            storePassword = keystoreProperties["storePassword"] as String
            keyAlias = keystoreProperties["keyAlias"] as String
            keyPassword = keystoreProperties["keyPassword"] as String
        }
    }
}

buildTypes {
    release {
        isMinifyEnabled = false
        signingConfig = signingConfigs.getByName("release")
        proguardFiles(...)
    }
}
```

### 4. Secure GitHub Secrets (for CI release builds)

In your GitHub repo → **Settings → Secrets and variables → Actions**:

- `KEYSTORE_BASE64` – base64 of the `.jks` file  
  ```bash
  base64 -w 0 nsboostbd-release.jks > keystore.b64
  ```
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Then extend the workflow to decode the keystore and run `assembleRelease`.

### 5. Build signed release APK locally

```bash
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

---

## Future Updates

1. Increase `versionCode` (must be higher than previous) and optionally `versionName` in `app/build.gradle.kts`.
2. Build a new signed APK with the **same keystore**.
3. Upload to Google Play Console (or distribute the APK directly).
4. Users who already have the app will receive the update (or can install over the existing app) without data loss or reinstall, as long as the signing key is identical.

---

## Permissions

Only the following are requested:

- `INTERNET`
- `ACCESS_NETWORK_STATE`

No contacts, location, camera, microphone, or storage permissions.

---

## Security Notes

- HTTPS enforced (`usesCleartextTraffic="false"`)
- No hardcoded credentials
- No unnecessary JavaScript bridges
- SSL certificate validation is **not** disabled
- Private keys and secrets are never committed

---

## Customization

- Change the website URL in `MainActivity.kt` (`websiteUrl` constant)
- Update colors in `res/values/colors.xml`
- Replace launcher icons in `res/mipmap-*` and the adaptive XML
- Splash screen uses the adaptive icon on a solid primary color background

---

## License & Usage

This project is provided as a complete, ready-to-use source for the NSBOOSTBD SMM Panel Android application. You may modify and distribute the app under your own terms. The underlying website (nsboostbd.com) remains independent.

---

**Built with ❤️ for NSBOOSTBD**
