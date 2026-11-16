# PocketPilot — APK build notes (Mon 2026-11-16)

## Deliverable
- **APK:** `app/build/outputs/apk/debug/app-debug.apk`
- **Size:** 70,710,457 bytes (~67 MB — ML Kit OCR native libs + SQLCipher for 4 ABIs)
- **SHA-256:** `327825c4587cb929bdad5f85254aba6852422df9ae4a24b77c62cd331823fa46`
- **Package:** `com.pocketpilot.app`, versionCode 1, versionName 0.1.0, minSdk 27, targetSdk 34
- **Signature:** debug keystore (Android Debug), apksigner verified
- **Install:** `adb install app/build/outputs/apk/debug/app-debug.apk`

## Why Gradle does NOT run here (proven, not assumed)
Gradle's launcher talks to its daemon over loopback TCP (127.0.0.1). The
sandbox intercepts ALL TCP from Java — even loopback — and answers with the
policy message ("Other TCP connections is turned off… switch other_tcp from
Deny to Ask"). Proven with a two-JVM loopback test: the server received
nothing, the client got the policy text. The Gradle daemon starts and listens
on 127.0.0.1:38077, but the client can never talk to it
("Could not receive a message from the daemon"). There is no in-process Gradle
mode. On a normal machine / Android Studio, `./gradlew :app:assembleDebug`
should work once the fixes below are in.

## How this APK was built (manual pipeline, no Gradle)
Script: `~/workspace/android-sdk/build_apk.py`
1. Dependencies: `~/workspace/android-sdk/resolve.py` fetched the full closure
   (295 artifacts, 0 failures) with curl into
   `~/workspace/android-sdk/local-maven` (712 files, 254.6 MB).
   `classpath.py` resolves the runtime classpath (102 artifacts, newest-wins).
2. Resources: `aapt2 compile` (app + 37 library res dirs) then `aapt2 link`
   with each library as an `-R` overlay unit (keeps per-library packages, so
   AppCompat's and Material's different `styleable/SearchView` don't clash).
3. Compile: `kotlinc` 2.1.21 from `kotlin-compiler-embeddable`
   (`~/workspace/android-sdk/kotlinc.sh`): `:core` then `:app` + generated
   R.java (38 files). No KSP needed — no Room annotations exist in the code.
4. Dex: R8 8.3.36 (`~/workspace/android-sdk/r8-8.3.36.jar`, AGP 8.3.1's own
   R8 from Google Maven). The d8 in build-tools 34.0.0 (8.2.2-dev) crashes
   with an NPE on AppCompat's `ActionBarDrawerToggle$1`; R8 8.3.36 handles it.
   Output: `classes.dex` + `classes2.dex` (multidex, 11,387 classes).
5. Package: dex + native libs (4 ABIs) + AAR assets into the linked APK,
   `zipalign`, `apksigner` with a generated debug keystore.

## Project fixes made to get it building (all in the repo)
1. `build.gradle.kts`: KSP `2.1.21-1.0.29` does not exist (KSP 1.0.x stops at
   Kotlin 2.1.10; for Kotlin 2.1.21 the KSP line is 2.0.x) → `2.1.21-2.0.2`.
2. `app/build.gradle.kts`: the KSP plugin was declared (`apply false`) but
   never applied, while the app uses `ksp(...)` → added `id("com.google.devtools.ksp")`.
3. `PilotAccessibilityService.kt` (`takeScreenshot`): `ScreenshotResult` has
   no `.bitmap` (verified with javap on android.jar) → use
   `result.hardwareBuffer` + `Bitmap.wrapHardwareBuffer(...)`, closing the
   buffer after wrapping.

## Notes
- No Room code exists yet (no @Database/@Entity/@Dao), so the Room + SQLCipher
  deps are compiled in but unused at runtime; KSP generates nothing.
- The 67 MB size is dominated by ML Kit OCR `.so` files (~44 MB across 4
  ABIs) and SQLCipher (~13.5 MB across 4 ABIs). Dropping x86/x86_64 would
  save ~23 MB (phones use arm64-v8a).
- To rebuild: `python3 ~/workspace/android-sdk/build_apk.py`
  (re-runs aapt2 → kotlinc → R8 → zipalign → apksigner; ~5 min).
