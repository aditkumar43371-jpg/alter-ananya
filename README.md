# Alter Ananya

Hindi/Hinglish voice assistant for Android (minSdk 26, targetSdk 34), package `app.alterananya`.
Built for TalkBack users. The user pastes his own Gemini API key in Settings; no key is stored in the code.

## Structure (app/src/main/java/app/alterananya)
Screens
- App.kt, MainActivity.kt, ConversationActivity.kt (talk screen with Haan/Nahi buttons), SettingsActivity.kt, FeatureSettings.kt (the per-feature switches, Hinglish), PermissionsActivity.kt + Perms.kt (guided permission setup), A11yIntroActivity.kt, Ui.kt
Settings and keys
- S.kt: settings; API keys only in encrypted storage; settings export/import never contains keys
Talking to the AI
- Llm.kt, Brain.kt (local commands first, then the model), Prompts.kt, Personalities.kt (built-in + your own, with overrides), Languages.kt
Voice
- VoiceIn.kt (speech to text, feeds the emotion cues), Speaker.kt (speech out, tone presets), Talk.kt, VoiceLoop.kt (wake, listen, speak, "ruko"), ListenService.kt (foreground "Ananya sun rahi hai"), WakeEngine.kt, WakeWords.kt, VoskModel.kt (offline wake word model)
Safety and privacy
- Safety.kt (pause/resume, loop stopper, secret-field hand-off), Confirm.kt (spoken or button yes/no, no answer = no), Redact.kt (hides OTP, card, password before the model), Vault.kt + MemoryStore.kt (memory with categories, optional encryption)
Features
- Tone.kt, Emotion.kt, ScreenQ.kt, Proactive.kt, Routines.kt, PhoneTools.kt (call, SMS, WhatsApp, torch, volume, media, timers, battery, DND, settings pages), Tools.kt (weather, YouTube, reminders, notes, web, open app, notification builder), NotifListener.kt, Overlay.kt, TextBits.kt, Launch.kt
Goal mode
- Agent.kt, AnanyaService.kt (accessibility service)
- Manifest and resources: app/src/main/AndroidManifest.xml, app/src/main/res

See PHASE1-STATUS.md for what each feature does, what is untested, and the permission list.

## Rebuild
Needs JDK 17, Gradle 8.7, Android SDK platform 34 and build-tools 34.0.0.
Put the SDK path in `local.properties` as `sdk.dir=/path/to/sdk`.

Debug build (signed with the debug key):

    gradle assembleDebug --no-daemon

Unit tests: `gradle testDebugUnitTest`

Output: app/build/outputs/apk/debug/app-debug.apk

Release build with your own keystore (never commit the keystore or password):

    keytool -genkeypair -keystore my.jks -alias alter -keyalg RSA -keysize 2048 -validity 10000
    ALTER_KS_PATH=/path/my.jks ALTER_KS_PASS=yourpassword gradle assembleRelease --no-daemon

Output: app/build/outputs/apk/release/app-release.apk. Check with `aapt dump badging` and `apksigner verify`.

## Status
Debug build and 11 unit tests pass. Never run on a real phone yet; expect runtime bugs. Details in PHASE1-STATUS.md.
