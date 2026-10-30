# Ayne (አይን)

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

See [ARCHITECTURE.md](ARCHITECTURE.md) for the full breakdown: request flows, SSE event contracts, language routing, latency budget, security model and scaling notes.

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

The app never talks to Gemini or Addis AI directly: all provider keys live on the server. The client only knows the backend base URL, which is supplied at build time.

## Tech stack

**Android app** — Kotlin, Jetpack Compose (Material 3), CameraX, OkHttp + OkHttp SSE, MediaRecorder, MediaPlayer, `Pcm16Decoder`, Coroutines, Jetpack ViewModel. `minSdk 24`, `targetSdk 36`.

**Backend** — Vercel serverless functions: `/api/see-and-describe`, `/api/see-and-describe/stream`, `/api/read-text/stream`, `/api/ask-question` (plus `am` / `om` routing variants).

**AI providers** — Google Gemini for vision reasoning; Addis AI for Amharic/Afaan Oromo text-to-speech and speech recognition.

## Repository layout

```
app/src/main/java/com/example/
├── MainActivity.kt              Compose entry point, screen and theme wiring
├── data/
│   ├── VisionApiService.kt      HTTP client: describe (single pass) + SSE streaming
│   ├── MirkuzVisionRepository.kt Session-aware repository, audio playback, temp-file handling
│   ├── VisualSessionManager.kt  Visual session lifecycle and follow-up routing
│   ├── AddisRealtimeClient.kt   Addis AI realtime voice client
│   ├── AddisRealtimeSse.kt      Realtime event parsing
│   └── VisionModels.kt          Request/response models
├── ui/
│   ├── VoiceAskScreen.kt        Ask Question screen
│   ├── MockLanguageScreens.kt   Language selection
│   └── theme/                   Colour, typography, Material theme
└── util/
    ├── CameraCaptureHelper.kt       Frame capture and JPEG compression
    ├── VoiceRecorderHelper.kt       Microphone capture
    ├── AudioPlaybackHelper.kt       PCM/MP3 playback
    ├── Pcm16Decoder.kt              16-bit PCM decoding for realtime audio
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
