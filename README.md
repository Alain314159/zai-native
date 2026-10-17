# Z.ai Native

Native Android client for chat.z.ai — 100% Kotlin + Jetpack Compose, no WebView for chat.

## Features

- 🔐 **WebView login** (one-time) — resolve captcha, login with Google/Email, token captured automatically
- 💬 **Native SSE streaming** — OkHttp EventSource, no WebView during chat
- 🔏 **X-Signature** — HMAC-SHA256 calculation (extracted from chat.z.ai JS bundle)
- 📝 **Markdown rendering** — code blocks, lists, formatting in responses
- 💾 **Room/SQLite** — local conversation history, persists across sessions
- 📋 **Long-press to copy** — copy any message to clipboard
- ⚙️ **Model selector** — choose between GLM-5.3, GLM-5.3-Flash, GLM-4.7, etc.
- 🌙 **Dark OLED theme** — pure black background, optimized for AMOLED
- 🔴 **Error handling** — session expiry detection, network errors, graceful degradation
- 📱 **Conversation list** — create, browse, delete conversations
- 🏗️ **GitHub Actions** — automatic APK builds with signing

## Architecture

```
Login (WebView, one-time)
  └─ Loads chat.z.ai → user solves captcha → logs in
  └─ App intercepts JWT token from localStorage/cookies
  └─ Token saved to DataStore + SharedPreferences

Chat (100% Native Compose)
  └─ OkHttp SSE → POST /api/agent/v2/chat/completions
  └─ SignatureInterceptor adds X-Signature header
  └─ StreamListener parses SSE chunks → updates UI
  └─ Messages persisted to Room/SQLite
```

## API Protocol (reverse-engineered from chat.z.ai)

| Component | Value |
|-----------|-------|
| Endpoint | `POST /api/agent/v2/chat/completions` |
| Auth | `Authorization: Bearer <jwt>` |
| Signature | `X-Signature: <hmac-sha256>` |
| Body | `{"model":"glm-5.3","messages":[...],"stream":true}` |
| Response | SSE `text/event-stream` |
| Key | `key-@@@@)))()((9))-xxxx&&&%%%%%` |

## Build

Download APK from [Actions](../../actions) or build locally:
```bash
./gradlew assembleRelease
```

## Tech Stack

- Kotlin 2.0.21 + Jetpack Compose (Material 3)
- OkHttp 4.12 + OkHttp-SSE (streaming)
- Room 2.7 (SQLite database)
- DataStore (encrypted token storage)
- Coil (image loading)
- Markdown renderer (com.mikepenz)
- GitHub Actions CI/CD with keystore signing

## License

MIT
