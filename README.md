# NSBOOSTBD Android Project

Android WebView application for https://nsboostbd.com/.

- Application ID: `com.nsboostbd.app`
- Display name: `NSBOOSTBD`
- Kotlin / Android Gradle Plugin project
- Firebase Messaging dependency and Firebase Android configuration included
- GitHub Actions workflow builds a debug APK and uploads it as an artifact

## Build in GitHub Actions

Push the project to the `main` branch or manually run **NSBOOSTBD Android APK Build** from the Actions tab. The workflow installs Gradle 8.9 directly and runs `gradle assembleDebug`; it does not depend on the Gradle wrapper JAR/scripts.

## Important limitations

This is a starter WebView project. Firebase client configuration and the Messaging dependency are included, but automatic order/payment/support push notifications are not implemented. They require a trusted server-side sender. The app has not been built or tested in this environment.
