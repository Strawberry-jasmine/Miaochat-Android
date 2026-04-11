# RelayChat Android

RelayChat Android is a native Kotlin + Jetpack Compose client that tracks the existing `projects/RelayChat-iOS/` feature set as closely as practical without turning the app into a wrapper.

## Current scope

- OpenAI-compatible provider configuration
- Secure API key storage via Android Keystore-backed encrypted file storage
- Responses API and Chat Completions request modes
- Reasoning, verbosity, web search, tool choice, response format, extra headers, and extra body JSON
- Multi-session local thread history with rename, duplicate, branch, clear, and regenerate
- Image attachments from the Android photo picker
- Markdown and fenced code block rendering with copy actions
- `intelalloc Codex` preset plus Codex config import
- Endpoint normalization to avoid duplicated path segments
- Debug APK, release APK, and release AAB build outputs

## Project layout

- `app/src/main/java/com/example/relaychat/core/`: models, request building, response parsing, endpoint logic
- `app/src/main/java/com/example/relaychat/data/`: settings persistence, secure key storage, Room thread storage
- `app/src/main/java/com/example/relaychat/ui/`: Compose app shell and screens
- `app/src/test/`: parity-focused unit tests
- `scripts/`: local Windows build helpers

## Toolchain

- Kotlin 2.2.21
- Android Gradle Plugin 8.13.2
- Gradle 8.13
- Compile / target SDK 36
- Min SDK 28
- JDK 17

## Local setup

1. Install JDK 17.
2. Install Android SDK platform 36 and build-tools 36.0.0.
3. Point Android Studio or `local.properties` at your SDK.
4. Optionally copy `keystore.properties.sample` to `keystore.properties` and fill in real release signing values.

## Build commands

PowerShell helpers:

- `powershell -ExecutionPolicy Bypass -File .\scripts\build-debug.ps1`
- `powershell -ExecutionPolicy Bypass -File .\scripts\build-release.ps1`
- `powershell -ExecutionPolicy Bypass -File .\scripts\build-bundle.ps1`

Raw Gradle commands:

- Debug APK: `.\gradlew.bat testDebugUnitTest assembleDebug`
- Release APK: `.\gradlew.bat testDebugUnitTest assembleRelease`
- Release AAB: `.\gradlew.bat testDebugUnitTest bundleRelease`

## Output paths

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release-unsigned.apk`
- Release AAB: `app/build/outputs/bundle/release/app-release.aab`

If you do not provide signing config, the release APK remains unsigned. The release AAB is also unsigned unless signing values are supplied through `keystore.properties` or the `RELAYCHAT_SIGNING_*` environment variables.

## Signing inputs

`build.gradle.kts` accepts either `keystore.properties` or environment variables:

- `RELAYCHAT_SIGNING_STORE_FILE`
- `RELAYCHAT_SIGNING_STORE_PASSWORD`
- `RELAYCHAT_SIGNING_KEY_ALIAS`
- `RELAYCHAT_SIGNING_KEY_PASSWORD`

`keystore.properties.sample` shows the file-based format.

## CI

`.github/workflows/relaychat-android.yml` validates unit tests plus debug build on push / pull request and can produce release artifacts from `workflow_dispatch`.
