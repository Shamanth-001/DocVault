# DocVault

A private Android app for storing personal documents such as Aadhaar, PAN, passport, driving licence and vehicle papers. Everything stays encrypted on the phone, and the text inside photos can be searched.

## Features

- AES-256 encryption, unlocked with a fingerprint or the phone's screen lock
- No internet permission, so the app can't send data anywhere
- On-device text reading (ML Kit bundled model) for searching inside photos
- Detects common Indian documents and fills in numbers, names and expiry dates
- Expiry alerts 30, 7 and 1 days before a document expires
- Family profiles
- Password-protected backup and restore
- Optional WhatsApp reminder button that opens a chat with the dates typed in
- Screenshots blocked, with auto-lock after 60 seconds

## Build

Requires JDK 17 and the Android SDK (platform 34).

```
gradlew :app:testDebugUnitTest
gradlew :app:assembleDebug
```

Release builds need a `keystore.properties` file and a signing key in the project root. Neither is in this repository.
