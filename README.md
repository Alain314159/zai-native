# Z.ai Native

Native Android client for chat.z.ai — 100% Kotlin + Jetpack Compose, no WebView for chat.

## Features
- **WebView login** (one-time, for captcha + OAuth)
- **Native SSE streaming** (no WebView during chat)
- **X-Signature** calculation (HMAC-SHA256)
- **Dark theme** optimized for OLED
- **Room/SQLite** for local history

## Architecture
```
Login (WebView) → captures JWT token → saves to DataStore
Chat (Compose) → OkHttp SSE → /api/agent/v2/chat/completions
```

## Build
Download APK from [Actions](../../actions) or build locally:
```bash
./gradlew assembleRelease
```
