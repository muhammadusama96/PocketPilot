# Privacy

PocketPilot is designed so that using it does not mean handing your phone
to a server.

## What stays on the device, always
- Screen content (UI tree, screenshots) — processed locally by the agent loop.
- Memory (notebook) and secrets (vault) — AES/GCM-encrypted, keys in Android
  Keystore. `allowBackup=false`, and backup/data-extraction rules exclude them.
- Voice: wake-word templates, ASR transcripts while in on-device mode.
- Downloaded on-device models.

## What leaves the device, and when
- **Cloud mode only:** the current task's messages + tool results are sent to
  the LLM endpoint you configured (e.g. DeepSeek/OpenAI/Gemini), under your
  own API key. Screenshots are sent only when the model explicitly asked for
  one, and at most once every few steps.
- **On-device mode:** nothing leaves the device for reasoning. The only
  outbound traffic is the model download (Hugging Face, once) and the app's
  version check.
- **Never, in any mode:** vault secrets (passwords, cards, API keys) are
  typed locally into fields and never enter the model's context, tool
  results, logs, or network traffic. Notebook memory is never sent wholesale
  — only short retrieved snippets.

## What the app does not do
- No analytics SDK, no crash reporting, no advertising ID.
- No account, no sign-up, no sync by default.
- No always-on microphone listening in v0.1 (push-to-talk / wake-word gated).
- No multi-tenant operation: one operator, their own devices.

## If you choose a cloud model
Your prompts go to that provider under their terms. Prefer on-device mode
for banking, messages, and anything sensitive — the router falls back to
cloud only when you allow it.
