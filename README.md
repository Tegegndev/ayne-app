# Ayne (አይን)

[![Release](https://github.com/Tegegndev/ayne-app/actions/workflows/release.yml/badge.svg)](https://github.com/Tegegndev/ayne-app/actions/workflows/release.yml)

**A voice-first AI visual assistant for blind and low-vision people in Ethiopia.**

Ayne turns a phone camera into a pair of eyes. Point it at the world, ask in Amharic or Afaan Oromo, and hear the answer spoken back — no screen reading required.

Built as the graduation capstone for the 5th round INSA Summer Camp, Software Development track.

---

## Table of contents

- [Why](#why)
- [Features](#features)
- [How it works](#how-it-works)
- [Architecture](#architecture)
- [Tech stack](#tech-stack)
- [Repository layout](#repository-layout)
- [Getting started](#getting-started)
- [Releases](#releases)
- [Accessibility design](#accessibility-design)
- [Testing](#testing)
- [Roadmap](#roadmap)
- [Credits](#credits)
- [License](#license)

---

## Why

More than 1.5 million people in Ethiopia live with blindness or low vision. Every mainstream visual-assistance app — Seeing AI, Be My Eyes, Envision — is English-first. None of them read Ethiopian Birr notes, understand Ge'ez script, or speak Amharic or Afaan Oromo.

That leaves millions of people locked out of tools the rest of the world already takes for granted. Ayne is built for them first: voice in, voice out, in their own language.

## Features

| Feature | What it does |
| --- | --- |
| **Describe Surroundings** | Captures one frame and describes the scene aloud — objects, layout, and spatial cues ("door, slightly left, about two meters ahead"). |
| **Read Text** | Verbatim extraction for books, notices, banknotes and prescription labels, spoken back to the user. Detects blurry frames and asks the user to retake. |
| **Ask Question** | Conversational visual Q&A. The user asks a question by voice about the current scene and hears a spoken answer. |
| **Follow-up questions** | Pressing volume-down keeps the conversation on the *same* image: the server caches the visual session, so turn 2+ sends only a session id instead of re-uploading megabytes of JPEG. |
| **Repeat last answer** | One large, always-visible control replays the last response — the most-used affordance in real blind-assistant apps. |
| **Languages** | Amharic (`am`), Afaan Oromo (`om`), English (`en`) fallback. |
| **Volume-key control** | Hands-free operation: volume up captures and asks, volume down follows up, long-press keeps normal volume control. |

## How it works

```
User presses volume-up
        │
        ├── Camera captures one frame (JPEG, compressed, ~150 KB)
        └── Microphone records the spoken question
        │
        ▼
Ayne backend (Vercel) ──► Gemini vision model  ──┐
        │                                        │
        └──► Addis AI text-to-speech  ◄──────────┘
        │
        ▼
Audio chunks stream back over SSE and start playing
on the first chunk (~700 ms) instead of waiting for the full file
```

Three deliberate design decisions follow from Ethiopian realities:

1. **Event-driven capture, not continuous streaming.** One frame per question instead of a live video feed. A full interaction costs roughly 300 KB of mobile data instead of several megabytes per minute, and it removes the constant camera/battery drain.
2. **Stream the audio, don't wait for it.** The backend emits OCR text first, then TTS audio chunks over Server-Sent Events. Playback begins on the first chunk, so the user hears the answer while the rest is still arriving.
3. **Cache the visual session server-side.** Follow-up turns reference a `sessionId` only, saving both upload bandwidth and model context.

## Architecture

At a glance:

```
┌─────────────────────────┐        HTTPS / SSE        ┌──────────────────────┐
│  Ayne Android app       │  ──────────────────────►  │  Ayne backend        │
│  Kotlin + Compose       │                           │  (Vercel functions)  │
│                         │  ◄──────────────────────  │                      │
│  CameraX   · OkHttp     │   OCR events + audio      │  Gemini vision  ─┐   │
│  MediaRecorder · SSE    │   chunks + transcripts    │  Addis AI TTS   ─┼─► │
│  MediaPlayer · ViewModel│                           │  Session cache  ─┘   │
└─────────────────────────┘                           └──────────────────────┘
```

The app is a **thin client**. It never talks to Gemini or Addis AI directly, holds no provider credentials, and carries no prompt logic beyond a base URL supplied at build time. All secrets and provider routing live on the server, so a decompiled APK leaks nothing usable and prompt changes ship without an app release.

**Streaming contract.** Streaming endpoints respond with Server-Sent Events in a fixed order: `event: ocr` (extracted text first), then `event: audio_chunk` fragments, then `event: done` or `event: error`. Playback starts on the first chunk, and current speech is stopped before every new request so answers never overlap.

**Session caching.** Turn 1 sends `imageBase64` and receives a `sessionId`. Follow-up turns send only the session id — no image — and the backend answers against the cached visual context.

**Language routing.** Each endpoint exists in language variants (e.g. `/api/see-and-describe/am`, `/api/read-text/om/stream`). The client derives the variant from the configured base URL plus the selected language.

**Latency budget.** Target: first spoken word within ~3 seconds of the user finishing their question. Frame capture + JPEG encode 150–400 ms, upload 200–900 ms, Gemini inference 0.8–2 s, first TTS chunk 300–700 ms.

**Data cost.** One describe request ≈ 180 KB up / 60–120 KB down; a follow-up ≈ 30 KB up (no image). Continuous video streaming would cost several MB per minute, which is why capture is an event, not a stream.

## Tech stack

**Android app** — Kotlin, Jetpack Compose (Material 3), CameraX, OkHttp + OkHttp SSE, MediaRecorder, MediaPlayer, `Pcm16Decoder`, Coroutines, Jetpack ViewModel, Firebase (App Check for client attestation). `minSdk 24`, `targetSdk 36`.

**Backend** — Vercel serverless functions: `/api/see-and-describe`, `/api/see-and-describe/stream`, `/api/read-text/stream`, `/api/ask-question` (plus `am` / `om` routing variants).

**AI providers** — Google Gemini for vision reasoning; Addis AI for Amharic/Afaan Oromo text-to-speech and speech recognition.

## Repository layout

```
app/src/main/java/com/ayne/
├── MainActivity.kt                  Edge-to-edge entry point and hardware key routing
├── viewmodel/
│   └── AyneViewModel.kt             UiState machine, user intent handling, audio playback
├── domain/
│   └── CompanionFallback.kt         Offline rule-based fallback responses
├── data/
│   ├── VisionApiService.kt          HTTP client: describe (single pass) + SSE streaming
│   ├── VisionRepository.kt          Session-aware repository, audio playback, temp files
│   ├── HttpClientProvider.kt        Shared OkHttpClient instance and request factory
│   ├── HttpRetryHelper.kt           Network and 5xx retry with exponential backoff
│   ├── VisualSessionManager.kt      Visual session lifecycle and follow-up routing
│   ├── AddisRealtimeClient.kt       Addis AI realtime voice client
│   ├── AddisRealtimeSse.kt          Realtime event parsing
│   ├── VisionModels.kt              Request/response models
│   ├── endpoints/
│   │   └── LanguageRouter.kt        Pure language and endpoint URL resolution
│   └── network/
│       └── VisionNetworkManager.kt  Streaming vision network client
├── ui/
│   ├── MainScreen.kt                Primary camera screen & action layout
│   ├── VoiceAskScreen.kt            Ask Question screen
│   ├── MockLanguageScreens.kt       Language selection previews
│   ├── components/
│   │   ├── AyneTopBar.kt            Top bar and language picker dialog
│   │   ├── AyneBottomBar.kt         Bottom controls (repeat answer)
│   │   ├── AyneActionCards.kt       High-contrast action cards
│   │   ├── AskQuestionPanel.kt      Ask question visual panel
│   │   ├── CompanionPanel.kt        Companion conversation panel
│   │   └── VisionSettingsDialog.kt  Endpoint configuration dialog
│   └── theme/                       Colour, typography, Material theme
└── util/
    ├── CameraCaptureHelper.kt       Frame capture and JPEG compression
    ├── VoiceRecorderHelper.kt       Microphone capture
    ├── AudioPlaybackHelper.kt       PCM/MP3 chunk playback
    ├── Mp3FrameAligner.kt           MP3 frame-boundary splitting for glitch-free chunk playback
    ├── TempAudioStore.kt            Lifecycle-managed temp audio file storage
    ├── VolumeKeyHandler.kt          Hardware volume button interaction
    ├── Pcm16Decoder.kt              16-bit PCM decoding for realtime audio
    ├── PermissionPromptSpeaker.kt   Speaks permission request prompts aloud
    └── ProcessingFeedbackHelper.kt  Haptics and spoken status cues
```

## Getting started

### Prerequisites

- Android Studio (Ladybug or newer) with JDK 17
- An Android device or emulator running API 24+
- Access to an Ayne backend instance (or your own deployment)

### Configure

```bash
cp .env.example .env
```

Set `API_BASE_URL` to your backend. All endpoints are derived from this one value:

```
API_BASE_URL=https://your-deployment.example.app
```

### Build and run

```bash
./gradlew assembleDebug            # build the debug APK
./gradlew installDebug             # install on a connected device
```

The APK lands in `app/build/outputs/apk/debug/`.

### Permissions

The app requests camera and microphone access on first use. Both are required. No account, no sign-in, and no personal data leaves the device other than the frame and audio needed to answer the user's question.

## Releases

Prebuilt, signed APKs are published automatically to [GitHub Releases](https://github.com/Tegegndev/ayne-app/releases) — no Android Studio required to install the app.

To cut a release, use the helper script (bumps the version, commits and tags):

```bash
./scripts/release.sh 1.0.1 --push
```

Or tag manually. Pushing a `v*` tag triggers the [Build & Publish Release workflow](.github/workflows/release.yml), which builds the signed APK and attaches it to the GitHub Release:

```bash
git tag v1.0.1 && git push origin v1.0.1
```

## Accessibility design

Accessibility is the product, not a checkbox:

- **Voice-first.** Every action produces spoken feedback; the screen is a convenience, never a requirement.
- **TalkBack compatible.** Every control carries a meaningful `contentDescription` and large touch targets.
- **Hardware keys.** Volume up / volume down drive the core loop, so a user can operate the app without looking at the screen. Long-press keeps the normal volume behaviour intact.
- **High contrast.** Pure black background with yellow and white accents, comfortably above WCAG AAA thresholds.
- **Repeat control.** One prominent button replays the last answer.
- **Interruption.** Any new input stops the current speech immediately rather than queuing behind it.

## Testing

```bash
./gradlew test          # unit + Robolectric tests
```

Coverage includes Ask Question streaming, Read Text streaming, JSON response normalisation, Oromo endpoint parsing, visual session management, and Addis realtime event parsing.

## Roadmap

- **Location awareness** — "where am I?" via GPS and reverse geocoding, plus location context for AI answers.
- **Offline speech recognition** — fine-tuned Whisper on the Afrivoice Ethiopia dataset (CC BY 4.0) for voice input without connectivity.
- **More Ethiopian languages** — Sidama, Tigrinya and Wolaytta.
- **On-device fallback** — lightweight description model for low or no connectivity.
- **iOS client** — sharing the same backend contract.

## Credits

- Built by **Tegegn Wukianos Tafese**, IT student at Wolaita Sodo University.
- Vision reasoning: **Google Gemini**.
- Amharic and Afaan Oromo voice: **Addis AI**.
- Speech data for planned offline models: **Afrivoice Ethiopia** by Digital Umuganda (CC BY 4.0).

Ayne is a scene describer, not a navigation device. It does not replace a cane, a guide dog, or a sighted companion.

## License

MIT — see [LICENSE](LICENSE).
