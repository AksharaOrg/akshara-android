# Akshara for Android

Akshara is a native Sinhala keyboard for Android. It works entirely on-device
and supports three input methods:

- **Smart Phonetic** — adaptive romanized Sinhala input
- **Phonetic** — direct phonetic transliteration
- **Wijesekara** — the standard Sinhala keyboard layout (SLS)

The application ID is `lk.org.akshara.keyboard`, and Android displays the input
method as **Akshara Sinhala**.

## Features

- Local Sinhala word and next-word suggestions
- Sinhala-aware emoji suggestions and a built-in emoji picker
- Optional, on-device learning from accepted words
- Optional clipboard history with pinned items
- Number, symbol, email, URL, phone, and decimal layouts
- Light, dark, and system themes
- Adjustable key spacing and keyboard height
- Full-width and one-handed layouts
- Configurable vibration, key sounds, and high-contrast keys
- Forgiving touch detection with local touch personalization

## Privacy

Akshara has no internet permission, analytics SDK, or cloud dependency. Text
composition, suggestions, learned words, emoji search, touch personalization,
preferences, and clipboard history are processed and stored on the device.

Clipboard history is disabled by default and is unavailable in restricted input
fields. Recent entries can be cleared from settings; pinned entries remain until
they are removed individually. Akshara's local application data is excluded from
Android cloud backup and device transfer.

## Requirements

- Android Studio or the Android command-line tools
- JDK 17
- Android SDK Platform 36

The included Gradle wrapper downloads the required Gradle version automatically.

## Build and test

Clone the repository, open this directory in Android Studio, or run the following
from a terminal:

```sh
./gradlew testDebugUnitTest assembleDebug lintDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install it on a connected device or emulator with:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Enable the keyboard

1. Open **Akshara** from the app launcher.
2. Tap **Enable Akshara**.
3. Enable **Akshara Sinhala** in Android's on-screen keyboard settings.
4. Return to Akshara and tap **Select active keyboard**.
5. Choose **Akshara Sinhala** from the keyboard picker.

Android does not allow an application to enable its own input method, so the
settings confirmation is required.

## Release builds

Release signing expects these two files in the project root:

```text
akshara-upload.jks
.akshara-upload-password
```

The password file must contain only the keystore/key password. The configured key
alias is `upload`. Both files, along with other common keystore formats, are
excluded by `.gitignore` and must never be committed.

Create a signed release bundle with:

```sh
./gradlew bundleRelease
```

The bundle is generated at:

```text
app/build/outputs/bundle/release/app-release.aab
```

Back up the upload keystore and its password securely. Losing them can prevent
future updates from being signed with the same upload key.

## Project structure

```text
app/src/main/java/org/akshara/ime/
├── data/       # Predictions, emoji data, learning, and clipboard storage
├── engine/     # Sinhala composition and transliteration rules
├── ime/        # Input method service, keyboard UI, and touch handling
└── settings/   # Keyboard preferences and setup screen
```

Unit tests are under `app/src/test/`. A debug-only editor activity used for
keyboard testing is under `app/src/debug/` and is not included in release builds.

## Bundled language data

The packaged Sinhala frequency, next-word, trigram, sentence-start, and emoji
index data was copied from the iOS implementation under
`../ios/AksharaKeyboard/Resources/`. The Android build is self-contained and does
not require that adjacent directory.

Attribution notices for the bundled models are included beside the data files in
`app/src/main/res/raw/`.

## Repository safety

The repository's `.gitignore` excludes generated builds, Gradle and Kotlin
caches, local Android SDK configuration, IDE metadata, environment files, and
release-signing credentials. Before committing, it is still good practice to
review the staged files:

```sh
git status
git diff --cached
```
