# Architecture

## 1. What Andee is (analysis, 16 Nov 2026)

Andee (kuafuai/andee, v0.15.0, HEAD `6255a4f`) is a Kotlin phone agent:

- **Stack:** Kotlin 2.1.21, AGP 8.3.1, Gradle 8.4, JDK 17–20, compile/target SDK 34,
  min SDK 27. AppCompat/Material Views (no Compose on Android), no Room/SQLite,
  no Firebase/analytics. Deps: OkHttp, Java-WebSocket, CameraX, ML Kit barcode, ZXing.
- **Modules:** `:app` (Android body), `:core` (platform-neutral Kotlin — brain,
  tools, voice protocols, wake DSP, ball model; no `android.*` imports),
  `:desktop` (macOS Compose app reusing `:core`). ~350 Kotlin files.
- **Entry points:** `SelfCheckActivity`, `AndeeApp`, `ScreenBodyService`
  (AccessibilityService), `BodyCoreService` (foreground orchestrator),
  `NotificationRelayService`, `AlarmReceiver`.
- **Driving apps:** AccessibilityService (`canRetrieveWindowContent`,
  `canPerformGestures`, `canTakeScreenshot`), tree dump with stable element
  ids, taps/swipes by normalised coords or text anchor, screenshots with an
  aim grid, typing via its own IME or an ADBKeyboard broadcast. Recorded
  flows replay locally and self-heal. Refuses to tap WeChat (tapping blacks
  its screenshots until data is cleared).
- **LLM backend:** one thin non-streaming OkHttp client to `/chat/completions`
  (defaults DeepSeek, `deepseek-flash`/`gpt-4o-mini`), modes `hub` / `local` /
  `codeflying` (hosted proxy default). `LocalBrain`: single-threaded loop,
  sequential tool calls with deadlines, rationed screenshots, context
  pruning/archiving. ~100 tools in `ToolSchemas.kt`. Keys user-typed only,
  shipped keys hard-coded empty, stored in plaintext SharedPreferences.
- **Voice:** Volcengine Huoshan ASR/TTS (binary WebSocket, `seed-tts-2.0`),
  Google Vertex Live API (Gemini transcribe/TTS), system TTS, on-device
  MFCC + DTW wake word (user enrols a phrase 3×).
- **Memory:** notebook = one AES/GCM-encrypted JSON blob (Keystore key),
  chat history as JSON files, vault = AES/GCM JSON (Keystore key), secrets
  never cross to the LLM. `allowBackup=false`, no analytics SDK.
- **Other:** peer-to-peer (mDNS, QR pairing, ECDH + HKDF + AES-GCM),
  meeting recorder, robot dog over USB-serial, CameraX + barcode, OpenGL
  emotion ball.

## 2. License position (why this is clean-room)

Andee's LICENSE is **Apache 2.0 with additional conditions (Part A)**,
Copyright 2026 KUAFUAI. It permits commercial use, modification and forks,
but Part A forbids: (a) removing/replacing/obscuring the product name, icon
or copyright notices shown to the user in a distributed derivative;
(b) operating it as a multi-tenant service without written authorisation;
(d) **shipping it inside another product without prior written authorisation
from KUAFUAI**. A shippable own-brand product built on Andee's code would
need that permission. PocketPilot therefore reuses **architecture ideas
only** — module split, agent-loop shape, tool vocabulary shape, privacy
rules — and contains **no Andee source, assets, name, or icon**. Its LICENSE
is stock Apache 2.0 for our own code.

## 3. PocketPilot design

```
pocketpilot/
  core/                 # pure Kotlin/JVM, zero android.* imports
    brain/  AgentLoop.kt      # plan -> act -> observe, one thread, deadlines
            LlmClient.kt      # thin OpenAI-compatible /chat/completions client
            ModelProvider.kt  # cloud / on-device / hub seam + router
            Prompt.kt, ContextPruner.kt
    tools/  Tool.kt           # registry, dispatcher, JSON Schema helpers
            ToolSchemas.kt    # built-in tool vocabulary (17 tools)
            McpServer.kt      # localhost MCP endpoint (tools/list, tools/call)
    memory/ Notebook.kt       # encrypted JSON blob abstraction
            Vault.kt          # secrets: typed locally, never in LLM context
            Embeddings.kt     # on-device embeddings + brute-force top-k index
    voice/  Voice.kt          # ASR/TTS interfaces + pipeline
            WakeWord.kt       # MFCC + DTW on-device wake word (real)
    screen/ ScreenState.kt    # UI tree model + fused grounder
    config/ BrainConfig.kt    # modes, endpoints, on-device model catalogue
  app/                  # Android body
    a11y/   PilotAccessibilityService.kt, ScreenController.kt
    service/ PilotCoreService.kt, NotificationRelayService.kt, AlarmReceiver.kt
    storage/ KeystoreStore.kt (AES/GCM + Keystore), NotebookStore, VaultStore
    voice/  AndroidVoice.kt (system TTS + ML Kit seam)
    model/  ModelDownloader.kt (Hugging Face), OnDeviceModel.kt (LiteRT/llama.cpp seam)
    ui/     MainActivity (self-check), SettingsActivity
```

### Design rules carried over (as principles, reimplemented)
1. `:core` stays Android-free so the brain can run on phone + desktop.
2. One agent-loop thread; tool calls sequential, each deadline-bounded.
3. Screenshots rationed, never attached to every tool result.
4. Secrets never enter the LLM context — typed locally only.
5. Memory in one encrypted blob + plain JSON files; keys user-supplied, never baked in.
6. Explicit refusal list for dangerous targets (WeChat tap).
7. `allowBackup=false`, no analytics by default.

### What's new vs Andee (2026 features — see docs/FEATURES.md)
- ADK-for-Android-shaped agent loop + Gemini Nano hybrid routing.
- Bring-your-own on-device SLM (LiteRT-LM Gemma 4 / llama.cpp GGUF) with a model picker + downloader.
- Fused screen grounding (tree + OCR + 2B vision grounder).
- MCP-shaped tools with a local MCP endpoint.
- On-device ASR/TTS via ML Kit GenAI + encrypted memory with local embeddings.
