# VocalVerify Call Guard

An Android-native, sideloadable prototype for a floating real-time call-safety HUD. It is deliberately native Kotlin rather than Flutter/React Native because `TYPE_APPLICATION_OVERLAY`, call-state callbacks, and foreground-service lifecycle are Android platform features.

## What is implemented

- An obsidian, draggable `TYPE_APPLICATION_OVERLAY` HUD with analyzing, high-risk, and genuine states; haptics; dismiss, scan, and details actions.
- A persistent foreground monitor that responds to `OFFHOOK`/`IDLE` and can restart after boot.
- A one-tap simulation panel: the CEO demo resolves to the crimson alert after two seconds; the executive demo resolves to the green verdict.
- A 16 kHz / mono / PCM-16 microphone recorder, grouped into 1.5-second buffers, and a WebSocket message matching the requested payload schema.
- A notification on connection: **Turn on Speakerphone for VocalVerify Live Scanning**.

## Important Android limitation

An ordinary sideloaded app cannot capture the remote party's cellular-call audio. `CAPTURE_AUDIO_OUTPUT` is a signature/privileged permission and will not be granted to this APK. The only supported generic fallback is microphone capture with the user knowingly enabling speakerphone; that also picks up ambient audio and is not suitable for covert monitoring. The manifest intentionally does not request `CAPTURE_AUDIO_OUTPUT`.

The caller number is also frequently redacted on modern Android versions, even with call-related permissions. Treat it as optional metadata.

## Configure and build

1. Open this directory in Android Studio (JDK 17) and let it install the declared Gradle/Android SDK dependencies.
2. In the app, set the endpoint to your server origin, for example `https://green-dog.trycloudflare.com`, `wss://your-space.hf.space`, or `https://your-service.run.app`. The service safely converts `http(s)` to `ws(s)` and adds `/ws/telephony/{device_id}`. Do not enter the path twice.
3. Install on a device, accept microphone/phone permissions, and enable “Display over other apps”.
4. Start with either simulation button. For a real call-state demo, tap **Start call-state monitor**, place a call, then manually enable speakerphone.

## Before any distribution

Use clear in-app consent, disclose microphone transmission and retention, authenticate the WebSocket, avoid uploading phone numbers unless necessary, add TLS certificate/pin and backend authorization, and obtain legal/privacy review for every jurisdiction where it will run.
