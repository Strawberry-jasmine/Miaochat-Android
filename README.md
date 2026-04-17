# Miaochat for Android

Miaochat is a native Android chat client built with Kotlin and Jetpack Compose. It supports OpenAI-compatible providers, local thread history, image attachments, provider presets, and configurable request controls such as reasoning, verbosity, web search, tool choice, and structured outputs.

The app name is `Miaochat`. The current package name is `com.example.relaychat`.

## features

- OpenAI-compatible provider configuration
- Secure API key storage backed by Android Keystore
- Responses API and Chat Completions request modes
- Reasoning, verbosity, web search, tool choice, response format, extra headers, and extra body JSON
- Local multi-thread chat history with rename, duplicate, branch, clear, and regenerate actions
- Image attachments from the Android photo picker
- Markdown and fenced code block rendering with copy actions
- `intelalloc Codex` preset and Codex config import
- Background reply execution support
- Debug APK, release APK, and release AAB build outputs

## tech stack

- Kotlin 2.2.21
- Android Gradle Plugin 8.13.2
- Gradle 8.13
- Compile SDK 36
- Target SDK 36
- Min SDK 28
- JDK 17

## layout

- `app/src/main/java/com/example/relaychat/core/` for models, request building, parsing, and endpoint logic
- `app/src/main/java/com/example/relaychat/data/` for settings persistence, secure key storage, and Room thread storage
- `app/src/main/java/com/example/relaychat/ui/` for Compose screens and app shell
- `app/src/test/` for unit tests
- `app/src/androidTest/` for device and emulator smoke checks
- `scripts/` for local Windows build helpers
- `release/github/` for release notes, install docs, and maintainer release docs

## local setup

1. Install JDK 17.
2. Install Android SDK platform 36 and build-tools 36.0.0.
3. Point Android Studio or `local.properties` at your SDK.
4. If you want signed release builds, copy `keystore.properties.sample` to `keystore.properties` and fill in real signing values, or provide the `RELAYCHAT_SIGNING_*` environment variables.

## build

PowerShell helpers:

- `powershell -ExecutionPolicy Bypass -File .\scripts\build-debug.ps1`
- `powershell -ExecutionPolicy Bypass -File .\scripts\build-release.ps1`
- `powershell -ExecutionPolicy Bypass -File .\scripts\build-bundle.ps1`

Raw Gradle commands:

- `.\gradlew.bat testDebugUnitTest assembleDebug`
- `.\gradlew.bat testDebugUnitTest assembleRelease`
- `.\gradlew.bat testDebugUnitTest bundleRelease`

## outputs

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK with signing: `app/build/outputs/apk/release/app-release.apk`
- Release APK without signing: `app/build/outputs/apk/release/app-release-unsigned.apk`
- Release AAB: `app/build/outputs/bundle/release/app-release.aab`

## signing

`build.gradle.kts` accepts signing values from either `keystore.properties` or environment variables:

- `RELAYCHAT_SIGNING_STORE_FILE`
- `RELAYCHAT_SIGNING_STORE_PASSWORD`
- `RELAYCHAT_SIGNING_KEY_ALIAS`
- `RELAYCHAT_SIGNING_KEY_PASSWORD`

The sample file at `keystore.properties.sample` shows the file format.

## ci

`.github/workflows/android.yml` runs unit tests and a debug build on push and pull request. The same workflow can also build release artifacts from `workflow_dispatch`.

## releases

Release notes, install instructions, and maintainer release docs live in `release/github/`. Release binaries are generated locally and are not tracked in Git.
