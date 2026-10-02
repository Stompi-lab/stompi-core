# Stompi Wallet for Android

Android wallet for STP on the Stompi network. **Version 0.8.0 · versionCode 11 · tech.stompi.wallet**.

Create and restore wallets, manage multiple wallets, sign and send payments locally, save contacts, view activity and news. Supports language selection, light/dark appearance and optional notifications.

## New in 0.8.0

Settings → Network provides manual HTTPS server selection. The server must expose compatible wallet APIs, including `/api/network`, and report the expected Stompi genesis and synchronized index. These server-provided checks are not independent blockchain verification. A selected server can see queried addresses and supply inaccurate data. Private wallet keys and recovery words remain on the device. Pending transaction IDs are retained; switching does not resend payments automatically.

The default server is `https://stompi.tech`. News also use the selected server; the news archive links to the official website. This repository contains the Android client only; it does not install a wallet API backend.

## Build

Open this repository root in Android Studio and sync Gradle. AGP 9.0.1, Gradle 9.1.0, Java 17, SDK 36, minimum Android API 26.

```sh
./gradlew testDebugUnitTest assembleDebug
```

Windows:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

For release APKs or Play Store bundles use your own private signing key. Never commit signing material, credentials or recovery words.

## Validation

Prepared from the uploaded 0.8.0 project. Java syntax and resource XML were checked during packaging. No Android SDK build or device test was performed as part of repository preparation. Existing historical documents may describe earlier development stages.

## License

Original app code: MIT, Copyright (c) 2026 Thomas Köhler (Stompi); see `LICENSE`. Dependencies retain their own licenses.

Security reports: **thomas@stompi.de**, privately; do not disclose private keys or recovery words. Source repository: https://github.com/Stompi-lab/stompi-android-wallet
