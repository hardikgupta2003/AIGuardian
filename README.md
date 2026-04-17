# AI Guardian

AI Guardian is an Android safety assistant built with Kotlin and Jetpack Compose. It combines emergency SOS actions, scam-call monitoring, medicine reminders, local persistence with Room, and Firebase event logging in a single app.

This project targets modern Android devices and currently builds with:

- Android Gradle Plugin `8.7.2`
- Kotlin `2.0.21`
- `compileSdk 35`
- `targetSdk 35`
- `minSdk 24`

## What the app does

- One-tap SOS trigger from the home screen
- Sends emergency SMS with current location link
- Logs SOS events to Firebase Realtime Database
- Runs a foreground safety service for location monitoring
- Starts in-call audio capture through `InCallService`
- Stores scam detection history locally with Room
- Stores medicine reminders locally
- Shows spoken medicine reminders through Text-to-Speech

## Current status

The app is functional as a development project, but a few parts are still incomplete or device-dependent:

- Scam monitoring depends on Android telephony/in-call behavior and device permissions
- Microphone capture is now isolated to the dedicated call-audio service
- Medicine reminders are saved to the database, but scheduling is not automatically wired from the add flow yet
- Emergency contacts are not yet managed from the UI and must be set in code
- Scam transcription/detection flow is scaffolded, but the STT chunk processing logic is currently commented out

So: this is a solid base app and demo project, but not yet a fully polished production build.

## Tech stack

- Kotlin
- Jetpack Compose
- Material 3
- Hilt
- Room
- Firebase Realtime Database
- Firebase Messaging
- Vosk speech recognition
- Google Play Services Location
- Accompanist Permissions

## Project structure

```text
app/src/main/java/dev/hardik/aiguardian/
|- data/
|  |- local/        Room database and DAOs
|  |- model/        Entities
|  |- remote/       Firebase integration
|  `- repository/   Repository layer
|- detection/       Scam detection and overlay logic
|- reminder/        Medicine alarm and receiver code
|- service/         Foreground services, boot receiver, in-call service
|- sos/             SOS and geo-safety logic
|- stt/             Voice/STT handling with Vosk
`- ui/              Compose screens and view models
```

## Requirements

Before running the app, make sure you have:

1. Android Studio Koala or newer
2. JDK 11
3. Android SDK 35
4. A physical Android device is strongly recommended for call and SMS features
5. Google Play services on the test device if you want location/Firebase behavior

## Setup

### 1. Open the project

Open the root folder in Android Studio:

`D:\AndroidProjectsKotlin\AIGuardian`

### 2. Sync Gradle

Let Android Studio sync the project, or run:

```powershell
.\gradlew.bat :app:assembleDebug
```

### 3. Firebase setup

The project already includes:

- [app/google-services.json](D:/AndroidProjectsKotlin/AIGuardian/app/google-services.json)

If you want to connect this app to your own Firebase project:

1. Create a Firebase project
2. Add an Android app with package name `dev.hardik.aiguardian`
3. Download your own `google-services.json`
4. Replace the existing file in `app/google-services.json`
5. Enable Realtime Database in Firebase Console

### 4. Vosk speech model setup

The app initializes Vosk in [AIGuardianApp.kt](D:/AndroidProjectsKotlin/AIGuardian/app/src/main/java/dev/hardik/aiguardian/AIGuardianApp.kt:16) using:

- model name: `model-en-us`

The corresponding model files must exist in the app assets under:

- `app/src/main/assets/model-en-us/`

If that folder is missing, speech recognition will not initialize correctly.

### 5. Build and install

From Android Studio, run the `app` configuration on a device.

Or from the terminal:

```powershell
.\gradlew.bat :app:installDebug
```

## Permissions used by the app

The app requests or declares these permissions:

- `RECORD_AUDIO`
- `READ_CALL_LOG`
- `READ_PHONE_STATE`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_MICROPHONE`
- `FOREGROUND_SERVICE_LOCATION`
- `SYSTEM_ALERT_WINDOW`
- `RECEIVE_BOOT_COMPLETED`
- `SEND_SMS`
- `CALL_PHONE`
- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`
- `READ_CONTACTS`
- `POST_NOTIFICATIONS`

Important notes:

- On Android 13+, notification permission must be granted manually
- On Android 10+, background location may need separate approval if you want boot/startup location monitoring to work reliably
- Microphone foreground service startup is tightly restricted on Android 14/15; this project now starts microphone capture only in the dedicated audio capture service

## How to use the app

## 1. First launch

When the app opens, it asks for the required runtime permissions. Grant them so the home screen and services can work.

If permissions are denied, the app stays on the permission-required screen.

## 2. Home screen

The home screen gives you:

- A large `SOS` button
- A `Medicines` card
- A `Scam Alerts` card

### SOS button

Tap the SOS button to trigger emergency mode.

What happens:

1. The app requests the current device location
2. It builds a Google Maps location link
3. It sends an SOS SMS to configured emergency contacts
4. It logs the event to Firebase

At the moment, emergency contacts are not configurable in the UI. They must be provided through `SOSManager.setEmergencyContacts(...)`.

Relevant file:

- [SOSManager.kt](D:/AndroidProjectsKotlin/AIGuardian/app/src/main/java/dev/hardik/aiguardian/sos/SOSManager.kt)

## 3. Medicine reminders

Open the `Medicines` screen from the home screen.

You can:

- View saved medicines
- Add a medicine with name, dosage, and time
- Delete a medicine entry

When a reminder fires, the app:

- Shows a notification
- Speaks the reminder aloud with Text-to-Speech

Current limitation:

- Medicines are stored in Room, but reminder scheduling is not automatically triggered after adding a medicine from the UI

Relevant files:

- [MedicineScreen.kt](D:/AndroidProjectsKotlin/AIGuardian/app/src/main/java/dev/hardik/aiguardian/ui/MedicineScreen.kt)
- [MedicineViewModel.kt](D:/AndroidProjectsKotlin/AIGuardian/app/src/main/java/dev/hardik/aiguardian/ui/MedicineViewModel.kt)
- [MedicineManager.kt](D:/AndroidProjectsKotlin/AIGuardian/app/src/main/java/dev/hardik/aiguardian/reminder/MedicineManager.kt)
- [MedicineReceiver.kt](D:/AndroidProjectsKotlin/AIGuardian/app/src/main/java/dev/hardik/aiguardian/reminder/MedicineReceiver.kt)

## 4. Scam alert history

Open the `Scam Alerts` screen from the home screen.

This screen shows locally stored scam events, including:

- phone number
- timestamp
- detected transcription text

Relevant files:

- [ScamLogScreen.kt](D:/AndroidProjectsKotlin/AIGuardian/app/src/main/java/dev/hardik/aiguardian/ui/ScamLogScreen.kt)
- [SafetyRepository.kt](D:/AndroidProjectsKotlin/AIGuardian/app/src/main/java/dev/hardik/aiguardian/data/repository/SafetyRepository.kt)

## 5. Background safety monitoring

The app starts `AIGuardianService` after permissions are granted in the main activity.

That service currently:

- starts as a location foreground service
- starts geo-safety monitoring
- stays sticky in the background

Relevant files:

- [MainActivity.kt](D:/AndroidProjectsKotlin/AIGuardian/app/src/main/java/dev/hardik/aiguardian/MainActivity.kt)
- [AIGuardianService.kt](D:/AndroidProjectsKotlin/AIGuardian/app/src/main/java/dev/hardik/aiguardian/service/AIGuardianService.kt)
- [GeoSafetyManager.kt](D:/AndroidProjectsKotlin/AIGuardian/app/src/main/java/dev/hardik/aiguardian/sos/GeoSafetyManager.kt)

## 6. Call monitoring

The project includes:

- `ScamInCallService` for telecom call callbacks
- `AudioCaptureService` for microphone foreground capture during a call

How it is intended to work:

1. Android binds the app's `InCallService`
2. When a call becomes active, the app starts `AudioCaptureService`
3. Audio chunks go to the STT engine
4. Transcriptions can be analyzed for scam keywords/patterns
5. Scam events can be stored in Room and shown in the log screen

Important:

- This behavior is heavily restricted by Android and OEM phone apps
- It may require enabling the app as the default calling assistant or default phone-related service depending on device behavior
- Not all devices allow third-party apps to capture call audio in practice

Relevant files:

- [ScamInCallService.kt](D:/AndroidProjectsKotlin/AIGuardian/app/src/main/java/dev/hardik/aiguardian/service/ScamInCallService.kt)
- [AudioCaptureService.kt](D:/AndroidProjectsKotlin/AIGuardian/app/src/main/java/dev/hardik/aiguardian/service/AudioCaptureService.kt)
- [ScamDetector.kt](D:/AndroidProjectsKotlin/AIGuardian/app/src/main/java/dev/hardik/aiguardian/detection/ScamDetector.kt)
- [VoiceCommandManager.kt](D:/AndroidProjectsKotlin/AIGuardian/app/src/main/java/dev/hardik/aiguardian/stt/VoiceCommandManager.kt)
- [VoskSTTEngine.kt](D:/AndroidProjectsKotlin/AIGuardian/app/src/main/java/dev/hardik/aiguardian/stt/VoskSTTEngine.kt)

## Running tests

Unit tests:

```powershell
.\gradlew.bat testDebugUnitTest
```

Instrumentation tests:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

APK build:

```powershell
.\gradlew.bat :app:assembleDebug
```

## Troubleshooting

### App crashes when starting foreground service

On Android 14/15, microphone foreground services are restricted. This project now avoids starting the microphone FGS from the main guardian service. If you reintroduce microphone startup from app launch or boot, the app may crash with a `SecurityException`.

### SOS sends no SMS

Check:

- `SEND_SMS` permission is granted
- emergency contacts were actually configured in code
- the device/SIM supports SMS sending

### No scam transcriptions appear

Check:

- Vosk model assets exist
- the in-call service is actually bound by the device
- `VoskSTTEngine.processAudioChunk()` is still incomplete in the current codebase

### Medicine reminders do not fire

Check:

- reminders are scheduled after creation
- notification permission is granted
- exact alarm behavior is not being limited by the device

## Recommended next improvements

If you continue development, these are the highest-value next steps:

1. Add emergency contact management UI
2. Automatically schedule medicine reminders after insert
3. Finish STT chunk parsing in `VoskSTTEngine`
4. Persist and display scam detection results end to end
5. Add onboarding for call-service and overlay permissions
6. Add proper background-location settings flow
7. Add tests for repositories, view models, and services

## License

No license file is currently included in this repository. Add one before public distribution.
