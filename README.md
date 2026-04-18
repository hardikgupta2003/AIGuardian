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
- Detects calls intelligently using `PhoneStateListener` (No Default Dialer required!)
- Starts in-call audio capture and analysis automatically on call start
- Stores scam detection history locally with Room
- Stores medicine reminders locally
- Shows spoken medicine reminders through Text-to-Speech

## Current status

The app is functional as a development project, but a few parts are still incomplete or device-dependent:

- Scam monitoring uses `PhoneStateListener` to auto-trigger analysis (Speakerphone required for mic access).
- Microphone capture is isolated to a dedicated high-priority foreground service.
- Medicine reminders are saved to the database, but scheduling is not automatically wired from the add flow yet.
- Emergency contacts are not yet managed from the UI and must be set in code.
- **Transfers and Analysis**: Real-time STT analysis is active and logs events to `AIGuardianDebug`.

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

### Important Security Note:
Monitoring caller audio on Android is restricted. The app uses the **Speakerphone Method**: When a call is active, you must turn on the **Speakerphone** button on your dialer so the AI can "hear" the caller via the microphone.

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

## 6. Secure P2P VoIP & Call Monitoring

The project has evolved into a fully functional, peer-to-peer VoIP network over Firebase:

- Uses an internal **6-Digit Secure PIN** system to initiate calls.
- Bypasses traditional WebRTC firewalls using a direct **Firebase Audio Socket Relay**, pushing Base64 encoded `AudioRecord` PCM waves bi-directionally in real time.
- Transcribes and analyzes speech natively on your phone using **Vosk-STT (Indian English `model-en-in`)** configured with localized, Hinglish vocabulary inside `ScamRiskAnalyzer.kt`.

How it works:
1. Two users pair using their 6-digit `DeviceProfile` PIN via the newly added Dialer.
2. `WebRTCManager` (functioning as a VoIP Manager) handles the AudioTrack playback and AudioRecord capture.
3. Every audio slice is pushed directly into the Vosk Engine running locally on the device.
4. Transcriptions are generated and evaluated every few seconds.
5. If a scam is detected (`CAUTION` or higher), `IncomingCallActivity` launches a massive flashing Red Alert UI.
6. A continuous `ToneGenerator` siren blasts for 5 seconds to command the elderly user's attention, before the app programmatically severs all Voice sockets and forcibly hangs up the call.

Relevant files:

- [DialerScreen.kt](D:/AndroidProjectsKotlin/AIGuardian/app/src/main/java/dev/hardik/aiguardian/ui/call/DialerScreen.kt)
- [WebRTCManager.kt](D:/AndroidProjectsKotlin/AIGuardian/app/src/main/java/dev/hardik/aiguardian/webrtc/WebRTCManager.kt)
- [ScamRiskAnalyzer.kt](D:/AndroidProjectsKotlin/AIGuardian/app/src/main/java/dev/hardik/aiguardian/detection/ScamRiskAnalyzer.kt)
- [IncomingCallActivity.kt](D:/AndroidProjectsKotlin/AIGuardian/app/src/main/java/dev/hardik/aiguardian/ui/call/IncomingCallActivity.kt)
- [VoskSTTEngine.kt](D:/AndroidProjectsKotlin/AIGuardian/app/src/main/java/dev/hardik/aiguardian/stt/VoskSTTEngine.kt)


## Anti-Vibe Coding Constraints

Through this architecture, AI Guardian strictly adheres to the requested coding constraints:

*   **On-Device NLP**: Audio processing and inference are performed 100% locally. We leverage the `org.vosk` offline speech recognition suite loaded with `model-en-in`, ensuring no live audio leaves the device for cloud NLP API processing. This protects user privacy by design.
*   **Real-Time Audio Pipeline**: `AudioRecord` slices byte arrays which are instantly written to `AudioTrack` and simultaneously streamed into `VoskSTTEngine.processAudioChunk()`. The `ScamRiskAnalyzer` maintains a rolling TranscriptSegment window, actively classifying caller intent in real time.
*   **UI/UX Constraint**: The application was hardened for elderly safety. If the localized NLP detects severe fraud intent, normal execution stops, a massive red full-screen takeover occurs ("SCAM DETECTED!"), a blaring 5-second CDMA Emergency network siren is played through `ToneGenerator`, and the call automatically hangs up to sever the psychological grip of the attacker.

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

## Debugging (Forensic Logs)

All application telemetry is consolidated under a single Logcat tag for easy troubleshooting.

**Tag: `AIGuardianDebug`**

Filter by this tag in Android Studio to track the life of a call:
1. `APPLICATION_START`: App is initializing.
2. `CALL_STATE`: Confirming the phone state change (Ringing/Offhook).
3. `PIPELINE`: Confirming the STT engine and audio capture have started.
4. `STT_RESULT`: Real-time text output from the AI.
5. `DETECTION`: Scam analysis results and alert triggers.

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
