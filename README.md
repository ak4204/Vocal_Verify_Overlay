# VocalVerify Ngrok Call Guard

Standalone Android app for the existing VocalVerify dashboard backend. It does **not** change the dashboard. Enter an ngrok HTTPS/WSS origin; the app sends 1.5-second, 16 kHz mono PCM-16 Base64 JSON frames to:

```text
wss://YOUR-NGROK-DOMAIN/ws/telephony/{device_id}
```

The JSON fields match the existing backend contract: `session_id`, `device_id`, `target_profile_id`, `telecom_metadata`, and `audio_payload.audio_bytes_base64`. It understands `ANALYZING`, `GENUINE`, `HIGH_RISK`, and `AI_IMPERSONATION` verdicts.

## Run it

1. Build the project with the Android GitHub Actions workflow (copy the workflow from the old app if this will use the same repository) or Android Studio.
2. Install the APK and grant Phone, Microphone, Notifications, and **Display over other apps** permissions.
3. Enter `https://YOUR-NGROK-DOMAIN` or `wss://YOUR-NGROK-DOMAIN`, save it, and tap **Enable Call Guard**.
4. On an active call, enable speakerphone. The floating HUD is created at the screen's top-right before network streaming starts.

## Sensitive-call warning

The app does not perform local keyword detection or speech-to-text. It only sends PCM audio to the configured backend and renders that backend's verdict.

## Android constraint

Normal sideloaded Android apps cannot capture the remote cellular-call audio stream. This app records only the device microphone, with clear notifications; speakerphone is required for the remote party to be audible. Use only with informed consent and applicable legal/privacy review.
