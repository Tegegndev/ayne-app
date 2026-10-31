# Ayne — Architecture

This document describes how Ayne is put together, why the pieces are shaped the way they are, and where the seams are for future work.

---

## 1. System overview

```
┌──────────────────────────────┐
│        Ayne Android app      │
│                              │
│  Compose UI (MainActivity)   │
│         │                    │
│    AyneViewModel             │
│         │                    │
│  VisionRepository ──────────┼──┐
│         │                    │  │  HTTPS + SSE
│  VisionApiService            │  │
│  VisualSessionManager        │  │
│  AddisRealtimeClient         │  │
└──────────┼───────────────────┘  │
           │                      ▼
           │            ┌─────────────────────────┐
           │            │   Ayne backend (Vercel) │
           │            │   serverless functions  │
           │            │                         │
           │            │  /api/see-and-describe  │
           │            │  /api/see-and-describe  │
           │            │        /stream          │
           │            │  /api/read-text/stream  │
           │            │  /api/ask-question      │
           │            │                         │
           │            │  provider keys, session │
           │            │  cache, prompt rules    │
           │            └───────┬─────────┬───────┘
           │                    │         │
           │              ┌─────▼───┐ ┌───▼─────┐
           │              │ Gemini  │ │ Addis AI│
           │              │ vision  │ │ TTS/STT │
           │              └─────────┘ └─────────┘
           │
     ◄─────┴──── audio chunks, transcripts, OCR metadata
```

**Trust boundary.** The app is a thin client. It holds no provider credentials, no prompt logic and no model configuration beyond a base URL. All secrets, prompt engineering and provider routing live in the backend, so a decompiled APK leaks nothing usable and prompt changes ship without an app release.

---

## 2. Request flows

### 2.1 Describe surroundings (single pass)

1. `CameraCaptureHelper` captures one frame, downscales and JPEG-compresses it (~150 KB).
2. Optional: the user's spoken question is recorded and attached as base64 audio.
3. `VisionApiService.seeAndDescribe()` POSTs JSON to `/api/see-and-describe/{am|om}`.
4. Backend calls Gemini vision with the frame (and audio, if present), gets a description, then synthesises speech with Addis AI.
5. Response returns `cleanSpeech` / `amharic` text plus an `audioBase64` field and a cached `sessionId`.
6. The repository plays the audio through `MediaPlayer` and the ViewModel publishes the text to the UI state.

### 2.2 Read text (streaming, SSE)

1. Frame captured as above.
2. `VisionRepository.readTextStream()` opens `POST /api/read-text/stream` with `Accept: text/event-stream`.
3. Backend emits, in order:
   - `event: ocr` — extracted text, so the app can show it while audio is still generating
   - `event: audio_chunk` — base64 audio fragments
   - `event: done`
   - `event: error` on failure
4. The repository writes chunks to a temp file and starts `MediaPlayer` on the **first** chunk rather than waiting for the complete file. This is the single largest perceived-latency win in the app.
5. `stopCurrentSpeech()` runs before each new request so answers never overlap.

### 2.3 Ask a question / follow-up turns

The interesting case. Two paths share one endpoint family:

**Turn 1 — new capture.** The request carries `imageBase64`. The backend runs vision + TTS and returns a `sessionId` for the captured scene.

**Turn 2+ — same scene.** The request carries only `sessionId`/`chatId`, no image. The backend reloads the cached visual context and answers the new question against it. This is the "zero-upload follow-up" path.

Client rules:

- Send client-side history **only when there is no active session**. When a `sessionId` exists the server already holds the conversation; resending history duplicates turns, inflates context and degrades latency after turn two.
- `resetVisualSession()` clears the id so the next question starts with a fresh capture.
- The volume-down gesture on the home screen is bound to the follow-up path, per the original interaction contract; volume-up starts a new capture.

---

## 3. Language routing

Every endpoint that produces speech exists in language variants:

```
/api/see-and-describe            → default (English)
/api/see-and-describe/am         → Amharic
/api/see-and-describe/om         → Afaan Oromo
/api/read-text/stream            → Amharic default
/api/read-text/{am|om}/stream    → per-language variants
```

The client derives the variant from a single configured base URL plus the selected language, rewriting the path segment with a regex (`/see-and-describe(?:/(?:am|om))?` → `/see-and-describe/om`). Voice and TTS provider are chosen the same way: Addis AI with the `am-hamen` voice for Amharic and Oromo, Gemini `Zephyr` for English.

> **Known sharp edge:** this path-rewriting logic is duplicated across `seeAndDescribe()`, `seeAndDescribeStream()` and the repository. A single `resolveEndpoint(language, path)` helper should own it. Until then, any change to routing must be applied in every copy.

---

## 4. Latency budget

Target: first spoken word within 3 seconds of the user finishing their question.

| Stage | Typical | Notes |
| --- | --- | --- |
| Frame capture + JPEG encode | 150–400 ms | Compression level is user-adjustable in settings |
| Upload | 200–900 ms | ~150 KB on Ethiopian mobile data |
| Gemini vision inference | 800–2000 ms | Dominant cost; short prompts help |
| First TTS chunk | 300–700 ms | Streaming path only |
| **First audio played** | **~1.5–3 s** | Streaming path; single-pass path adds full TTS time |

Levers, in order of impact: stream TTS (already done), keep prompts tight, cap image dimensions, avoid re-uploading frames on follow-ups (already done via session cache).

---

## 5. Data cost model

Mobile data in Ethiopia is metered and expensive. Design rule: never stream video.

| Interaction | Approximate data |
| --- | --- |
| One describe request | ~150 KB image + ~30 KB audio up, ~60–120 KB TTS down |
| Follow-up question | ~30 KB up (no image), ~60–120 KB down |
| Continuous video streaming (rejected design) | several MB per minute |

This is why the app treats capture as an event, not a stream.

---

## 6. Accessibility architecture

- **Input:** hardware volume keys drive the core loop; the on-screen cards are an alternative, not a requirement. Long-press passes through to normal volume control so users can still adjust loudness by feel.
- **Output:** every transition produces a spoken cue plus a distinct haptic pattern (`ProcessingFeedbackHelper`), so the user always knows whether the app is listening, thinking or speaking.
- **Interruption:** new input stops current playback immediately (`stopCurrentSpeech()`); no queueing behind a long answer.
- **Semantics:** all interactive composables carry `contentDescription`; touch targets are large and the palette is high-contrast by design.
- **Failure:** errors surface as spoken messages, never silent catches. A blind user cannot debug a blank screen.

---

## 7. Security

- Provider API keys live only in the backend environment; the repository contains none, and `.env.example` carries no secrets.
- The client is configured at build time with a single base URL.
- No user accounts, no persistence of captured frames on device beyond temporary files, and no analytics.
- Temp audio files are created per request and should be deleted after playback (see hardening list in §9).

---

## 8. Known limitations

1. The realtime voice client and the streaming vision client are independent; unifying them under one session abstraction would simplify state handling.
2. Offline STT/TTS fallback on device is not yet present; network connectivity is required for Amharic and Afaan Oromo synthesis.

*(Note: Prior limitations regarding path rewriting, monolithic MainActivity, silent catches, temp audio accumulation, lack of retry, and duplicate HTTP clients have been resolved in the refactor).*

---

## 9. Future work

- **Offline STT:** fine-tune Whisper on Afrivoice Ethiopia (CC BY 4.0) and run it on device via whisper.cpp, removing the STT network dependency.
- **Location context:** GPS + reverse geocoding for "where am I" and to ground AI answers in the user's area.
- **Additional languages:** Sidama, Tigrinya, Wolaytta.
- **Provider abstraction:** a single interface over Gemini / Addis AI so either can be swapped or run in parallel for comparison.

---

## 10. Repository map

| Path | Responsibility |
| --- | --- |
| `MainActivity.kt` | Edge-to-edge entry point, setContent, volume key routing |
| `viewmodel/AyneViewModel.kt` | Screen state machine, action dispatcher, audio orchestration |
| `ui/MainScreen.kt` | Scaffold, camera preview, high-contrast action cards |
| `ui/components/` | Top bar, bottom bar, action cards, companion panel, settings |
| `data/VisionApiService.kt` | HTTP client: describe (single pass) + SSE streaming |
| `data/VisionRepository.kt` | Session-aware orchestration, audio playback, temp files |
| `data/network/VisionNetworkManager.kt` | Streaming vision client and visual session management |
| `data/endpoints/LanguageRouter.kt` | Pure language and endpoint resolution |
| `data/HttpClientProvider.kt` | Shared OkHttpClient and request builder |
| `data/HttpRetryHelper.kt` | Exponential backoff retry for network and 5xx errors |
| `data/VisualSessionManager.kt` | Visual session lifecycle and follow-up routing |
| `data/AddisRealtimeClient.kt`, `AddisRealtimeSse.kt` | Addis AI realtime voice transport and parsing |
| `data/VisionModels.kt` | Request/response data models |
| `domain/CompanionFallback.kt` | Rule-based fallback answers when backend is unreachable |
| `util/TempAudioStore.kt` | Cache-dir audio file lifecycle and size capping |
| `util/` | Camera capture, voice recording, playback, PCM decoding, feedback cues |
| `app/src/test/` | Unit and Robolectric tests |

---

*Ayne is a scene describer and reader, not a navigation aid. It does not replace a cane, a guide dog, or a sighted companion.*
