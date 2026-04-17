# AI Guardian - Agent Architecture & Forensic Guide

This document is for future AI coding agents working on the AIGuardian project. It outlines the current "Forensic Debugging" architecture and the logic behind the latest pivot.

## Current Call Detection: PhoneStateListener Logic
As of April 2026, the app has pivoted from `InCallService` (Modern Dialer Role) to **`PhoneStateListener`** (Legacy/Standard Telephony) for maximum compatibility and easier onboarding.

### Key Components:
1.  **`AIGuardianService`**: The primary background engine. 
    - USES: `TelephonyManager.listen()` to monitor `CALL_STATE_RINGING`, `OFFHOOK`, and `IDLE`.
    - ACTION: Automatically starts/stops the `AudioCaptureService` and `OverlayManager` based on these states.
2.  **`AudioCaptureService`**: Handles the heavy lifting of microphone monitoring.
    - TRICK: Operates on the assumption that the user will turn on **Speakerphone** so the mic can "hear" the caller.
3.  **`ScamDetector`**: The "Brain."
    - USES: `VoskSTTEngine` for offline, local speech-to-text.
    - ACTION: Compares transcripts against scam patterns and updates `ScamProtectionState`.
4.  **`OverlayManager`**: The "Visual Warning System."
    - USES: `WindowManager` to draw Compose-based UI over other apps.
    - ALERT: Triggered when `ScamDetector` identifies a threat level of `HIGH` or `SEVERE`.

## Debugging Workflow
Always filter Logcat by the following tag for a full forensic trace:
**Tag: `AIGuardianDebug`**

### Log Milestones to watch:
- `APPLICATION_START`: App process is alive.
- `ROLE_FORENSICS`: Shows current default dialer status (Note: Match=false is expected in current design).
- `SERVICE_HEARTBEAT`: Confirms the background service is running.
- `CALL_STATE`: Confirming the system notified the app of a call.
- `PIPELINE`: Confirming audio monitoring started.
- `STT_RESULT`: Real-time transcripts from the caller.

## Why the pivot to PhoneStateListener?
While `InCallService` allows for auto-hanging up, many users found the "Default Phone App" requirement too invasive. `PhoneStateListener` allows the app to stay in the background and analyze calls with only the `READ_PHONE_STATE` permission, making it significantly more likely to be used by the target demographic (elderly users).

## Future Improvements:
- [ ] Migrate to `TelephonyCallback` for Android 12+ (API 31) while keeping `PhoneStateListener` fallback.
- [ ] Implement "Smart Speaker Detection" (automating the speakerphone intent if possible).
- [ ] Enhancing the `ScamDetector` with more localized/region-specific scam patterns (e.g., India-specific UPI scams).
