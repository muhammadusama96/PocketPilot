# 2026 features — chosen, with sources and implementation status

Research date: 16 Nov 2026. Rule applied: free/open-source, no paid service,
no account required, actually implementable by a small project.

## Chosen (6)

### 1. On-device agent loop — Google ADK for Android + Gemini Nano
ADK for Android v0.1.0 (announced 21 May 2026, open-source Kotlin) runs agents
inside the app with sequential/parallel flows, tools, sessions; hybrid switch
Gemini Nano on-device (ML Kit GenAI/AICore) vs cloud Gemini. Nano 4 (Gemma 4
based, Apr 2026) reported on 140M+ devices. ML Kit Prompt API + Agent API
(`genai-agent` 1.0.0-alpha1, `AgentRunner`) automate plan → typed tool → observe.
- Sources: https://developers.googleblog.com/adk-kotlin-android-building-ai-agents/ ·
  https://www.luciq.ai/blog/android-on-device-ai-gemini-nano-guide
- Status: **our own loop is real** (`core/brain/AgentLoop.kt`); ADK library
  wiring is an integration point (dep commented in `app/build.gradle.kts`).
  Caveats designed around: ADK is experimental 0.1, Nano is foreground-only,
  ~<4,000-token context, per-app quotas — tasks stay short/chunked with a
  bundled-SLM fallback.

### 2. Bring-your-own on-device SLM — LiteRT-LM / MediaPipe + llama.cpp/GGUF
Gemma 4 E2B (~2.3B eff., ~2.6 GB int4 `.litertlm`) and E4B under Apache-2.0,
native function-calling, 128K context; LiteRT-LM runs on CPU/GPU/NPU.
GGUF fallback: Qwen3 1.7B (~1.1 GB), Phi-4-mini 3.8B (~2.7 GB),
Llama 3.2 3B (~2.2 GB), SmolLM2 1.7B via llama.cpp.
- Sources: https://marcmayol.com/blog/en/gemma-on-android-on-device-llm/ ·
  https://www.promptquorum.com/power-local-llm/mobile-llm-models-phi4-gemma-smollm
- Status: **model catalogue + Hugging Face downloader real**
  (`config/BrainConfig.kt`, `app/model/ModelDownloader.kt`); native inference
  call is a TODO in `OnDeviceModel.kt` pending the runtime dep.

### 3. Operate-any-app — Accessibility Service + UI tree + on-device OCR
`AccessibilityService` reads the foreground app's `AccessibilityNodeInfo` tree
and performs real taps/swipes/typing/scroll — no root, no ADB, no per-app API.
Screenshots + ML Kit on-device OCR cover canvas/WebView content the tree
can't see. Proven pattern of Mobilerun (ex-DroidRun, MIT, 10k stars, Nov 2026)
and Mika (Apache-2.0, Oct 2026, BYO-model with main/vision/light roles).
- Sources: http://dev.to/priya_negi_9ffd29931ea408/10000-stars-on-github-our-journey-building-mobilerun-in-public-37i4 ·
  https://explainx.ai/blog/mika-open-source-android-ai-agent-hidden-background-screen-apps-mcp-apache-2026
- Status: **implemented** (`a11y/PilotAccessibilityService.kt`,
  `ScreenController.kt`); ~40 scripted no-AI shortcuts deferred to v0.2.

### 4. Screen understanding — fused tree + screenshot vision, tiny on-device grounder
Fuse the accessibility tree (exact bounds) with a screenshot vision model that
returns where to tap. Open-weight SOTA 2026: Qwen-UI-Agent (MAI-UI-2B/8B,
Aug 2026; 92.2% MobileWorld-Real) and GUI-Owl 1.5 (Qwen3-VL 2B fine-tune);
Opposable (Oct 2026) ran GUI-Owl 1.5 4-bit on-phone via llama.cpp.
- Sources: https://ainews.personastack.ai/posts/alibaba-qwen-ui-agent-gui-benchmark-open-weights-2026 ·
  https://www.aiinsiders.net/article/a-small-ai-that-runs-a-phones-apps-without-ever-leaving-the
- Status: **tree grounding + fused grounder real** (`screen/ScreenState.kt`);
  vision grounder is a seam (`VisionGrounder`, TODO v0.2) validated against
  a fresh tree before executing.

### 5. MCP as the tool layer + Android 16 AppFunctions
MCP (spec 2026-07-28, TS SDK v2) standardises tool discovery/calling
(`tools/list`, `tools/call`). The phone is an MCP client/host: its own
capabilities are typed tools; Android 16 AppFunctions (Jetpack 1.0.0-alpha13,
7 Oct 2026) is Google's mobile equivalent.
- Sources: https://dev.to/gargpadmakar/mcp-server-with-android-a-practical-guide-to-model-context-protocol-kotlin-ai-agents-17jc ·
  https://www.voiceos.com/blog/agentic-android-os-mcp
- Status: **implemented** — JSON-Schema tool registry + dispatcher
  (`tools/Tool.kt`), OpenAI function-schema export, localhost MCP endpoint
  (`tools/McpServer.kt`, `tools/list`/`tools/call`/`ping`, loopback only).

### 6. Voice-first + encrypted memory — on-device ASR/TTS + Room/SQLCipher + Keystore
Google announced on-device ASR/TTS in AI Edge Gallery on 10 Nov 2026 (ML Kit
GenAI Speech Recognition/TTS via AICore; ASR 100+ languages, ~10× real-time;
TTS ~100 ms onset, 24 kHz). Open-weight fallbacks as `.litertlm`:
Parakeet-TDT 0.6B (ASR), Kokoro-82M / PocketTTS (TTS). Memory: Room/SQLite
over SQLCipher (AES-256), keys in Keystore, secrets in EncryptedSharedPreferences,
local embeddings (EmbeddingGemma) + local vector index.
- Sources: https://developers.googleblog.com/on-device-speech-recognition-and-text-to-speech-in-google-ai-edge-gallery/ ·
  https://copyprogramming.com/howto/sqlite-encryption-for-android
- Status: **wake word real** (MFCC + DTW, `voice/WakeWord.kt`); **voice
  pipeline interfaces + system TTS real**; **encrypted notebook/vault real**
  (Keystore AES/GCM); **embeddings + top-k index real** (hash embedder for
  tests — swap in EmbeddingGemma); ML Kit speech wiring is an integration
  point; push-to-talk/wake-word gated, no always-on listening in v0.1.

## Rejected (considered, not implementable for this brief)

1. **Gemini Computer Use API (GA 30 Oct 2026)** — paid tier only; screenshots +
   UI decisions go to Google's cloud. Violates free + on-device privacy.
2. **Qwen-UI-Agent 8B / AutoGLM-Phone-9B as on-device grounder** — too
   large/slow/RAM-hungry for a phone as an always-on step; 2B variant only.
3. **Always-on ambient listening** (e.g. VoxNexus, Oct 2026) — proprietary,
   always-on mic, battery/privacy trade-offs a small open project can't audit.

## Build order used

MVP core = Feature 3 (Accessibility) + Feature 1 (own loop, Nano-ready) +
Feature 2 (BYO SLM seam + downloader). Then Feature 4 (2B vision grounder),
Feature 5 (MCP tools), Feature 6 (voice + encrypted memory).
