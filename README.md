# Alter Ananya

Hindi/Hinglish voice assistant for Android (minSdk 26, targetSdk 34), package `app.alterananya`.
Built for TalkBack users. The user pastes his own Gemini API key in Settings; no key is stored in the code.

## Structure (app/src/main/java/app/alterananya)
- App.kt, MainActivity.kt, SettingsActivity.kt, A11yIntroActivity.kt: screens
- Ui.kt: small helper for building accessible screens
- S.kt: settings and encrypted key storage
- Llm.kt, Brain.kt, Prompts.kt, Personalities.kt, Languages.kt: talking to the AI model
- Talk.kt, VoiceIn.kt, Speaker.kt: voice in and out
- Tools.kt, MemoryStore.kt: reminders, app opening, memory
- Agent.kt, AnanyaService.kt: Goal mode (accessibility service)
- Manifest and resources: app/src/main/AndroidManifest.xml, app/src/main/res (icon is drawable/ic_launcher.xml)

## Rebuild
Needs JDK 17, Gradle 8.7, Android SDK platform 34 and build-tools 34.0.0.
Put the SDK path in `local.properties` as `sdk.dir=/path/to/sdk`.

Debug build (signed with the debug key):

    gradle assembleDebug --no-daemon

Output: app/build/outputs/apk/debug/app-debug.apk

Release build with your own keystore (never commit the keystore or password):

    keytool -genkeypair -keystore my.jks -alias alter -keyalg RSA -keysize 2048 -validity 10000
    ALTER_KS_PATH=/path/my.jks ALTER_KS_PASS=yourpassword gradle assembleRelease --no-daemon

Output: app/build/outputs/apk/release/app-release.apk. Check with `aapt dump badging` and `apksigner verify`.

## Status
Compiles cleanly. Never run on a real phone yet; expect runtime bugs.
