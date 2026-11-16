# PocketPilot

Your phone, piloted by you. An on-device-first AI phone agent for Android:
it reads the screen and operates any app like you do — no root, no ADB,
no account required. Memory and passwords stay on the device.

**Clean-room project.** PocketPilot was built by reverse-engineering the
*architecture* of the open-source phone agent Andee (kuafuai/andee) and
reimplementing it from scratch with new 2026 features. No Andee code,
assets, name, or icon are used. Andee is Apache 2.0 with additional
conditions that forbid white-labelling and shipping derivatives inside
another product without KUAFUAI's written authorisation — so this project
is original code throughout. See `docs/ARCHITECTURE.md` for the analysis
and the license position.

## What it does

- **Operates any app** via Android's Accessibility Service (tree + gestures + screenshots), no root.
- **Any LLM backend**: on-device small models (Gemma/Qwen/Phi via LiteRT-LM or llama.cpp) or any OpenAI-compatible cloud endpoint. You bring your own key; nothing is baked into the build.
- **Voice-first**: on-device wake word (MFCC + DTW, enrolled on the phone), on-device ASR/TTS where available, system TTS fallback.
- **Memory that stays home**: notebook + vault encrypted with AES/GCM, keys in Android Keystore. Secrets are typed locally and never enter the model's context.
- **MCP-shaped tools**: every capability is a typed tool with a JSON Schema; a local MCP endpoint on localhost lets other agents drive the same tools.
- **Screen grounding**: accessibility tree first, on-device OCR for WebViews/canvas, tiny on-device vision grounder as fallback.

## Status (v0.1.0 — honest)

| Area | Status |
|---|---|
| `:core` agent loop, tools, MCP server, memory, wake word | Implemented + unit-tested (runs on JVM) |
| Accessibility driving (tree, tap, swipe, type, screenshot) | Implemented (Android) |
| Encrypted notebook + vault (Keystore AES/GCM) | Implemented (Android) |
| Model downloader (Hugging Face) | Implemented |
| Cloud LLM (OpenAI-compatible) | Implemented |
| On-device inference (LiteRT-LM / llama.cpp) | Seam + downloader real; native call is a TODO — needs the runtime dep (see `OnDeviceModel.kt`) |
| ADK for Android / Gemini Nano loop | Our own loop is real; ADK library wiring is an integration point (dep commented in `app/build.gradle.kts`) |
| ML Kit GenAI on-device ASR/TTS | Interface + system TTS fallback real; ML Kit wiring is an integration point |
| Vision grounder (GUI-Owl 1.5 2B) | Interface + tree grounding real; GGUF inference TODO |
| Settings UI, overlay ball | Shells (v0.1) |
| Desktop (macOS) | Not started |

## Build & run

Prerequisites: JDK 17, Android Studio (Hedgehog+) with Android SDK 34.
The Gradle wrapper (`gradlew`, Gradle 8.4) is included; first run downloads
the distribution.

```bash
# 1. Check the pure-Kotlin core compiles and passes tests (no Android SDK needed):
./gradlew :core:test

# 2. Run the JVM demo (scripted model + fake screen, no network):
./gradlew :core:runDemo        # or: java -cp core/build/libs/... com.pocketpilot.core.DemoMain

# 3. Build the Android app (needs the SDK):
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

> **Build status note (2026-11-16):** `:core` compiles clean and its 9
> unit tests pass (verified with kotlinc 2.1.21 + JUnit 5 on the JVM).
> `:app` has **not** been compiled yet — it needs an Android SDK and a
> Gradle run on a normal machine (Gradle's daemon uses loopback TCP, which
> the sandbox this was built in blocks). The Android sources were checked
> against the public Android API stub: the only issues found were a
> self-referencing TTS initialiser (fixed) and missing androidx/R symbols
> that only the real Android build provides. Open the project in Android
> Studio and run `:app:assembleDebug` to verify.

**No-Gradle check of `:core`** (what was used here): with kotlinc 2.1.21
and okhttp/okio/json jars on the classpath,
`kotlinc core/src/main/kotlin -cp <deps> -d core.jar`, then the JUnit
console launcher for `core/src/test/kotlin/.../CoreTests.kt`.

Then: open the app, enable the accessibility service, pick a brain mode in
Settings (on-device model download, or paste a cloud API key), and give it
a task. Confirm irreversible actions (pay / send / delete) when asked.

## Docs

- `docs/ARCHITECTURE.md` — the Andee analysis and this project's design
- `docs/FEATURES.md` — the 2026 features chosen, with sources and status
- `docs/PERMISSIONS.md` — every permission and why it exists
- `docs/PRIVACY.md` — what leaves the device (almost nothing) and when

## License

Apache License 2.0 — see `LICENSE`. This project is original work; it is
not affiliated with, endorsed by, or derived from Andee/KUAFUAI.
