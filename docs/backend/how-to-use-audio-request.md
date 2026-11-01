# Backend request: "How to use" guide narration audio

## What this is

Ayne shows a spoken "how to use" guide to new users right after they pick a
language, and lets them replay it anytime from the top-bar help button. The app
plays **one pre-recorded audio file** (not device TTS — device Amharic voices
are poor) while showing the same text as an on-screen subtitle.

We need you to generate the Amharic narration from the script below and expose
it through the existing permission-guidance manifest.

## Voice direction (important — audience is blind users)

- This is likely the **first thing a blind user ever hears from Ayne**. Warm,
  calm, reassuring, unhurried — a helpful human guide, not an announcement.
- Speak **slower and clearer than normal conversation**, with clean Amharic
  diction. The listener cannot fall back on reading the screen.
- **No background music, no effects, single voice, one take.**
- Pause **~700 ms between the numbered sections** below and ~300–400 ms at
  sentence breaks inside a section.
- Feature names must be spoken **exactly as written** — they match the on-screen
  buttons the user will look for: ዙሪያውን አስረዳ / ጽሑፍ አንብብ / Read Text (English
  as written), and "Next" in section 7.
- The volume-key sentence is an instruction the user will physically act on:
  make የድምፅ መጨመሪያ (volume **up**) vs የድምፅ መቀነሻ (volume **down**) clearly
  distinguishable.
- Target duration: **60–90 seconds**.

## Script — Amharic (final, client-approved; do not paraphrase)

Section labels are production notes only — read the Amharic text, not the labels.

**[1 — Welcome]**
እንኳን ወደ አይኔ በደህና መጡ። አይኔ ዙሪያዎን የሚያሳይዎት የእርዳታ ኤ እይ መተግበሪያ ነው።

**[2]**
ሦስት ዋና አገልግሎቶች አሉ።

**[3 — Describe Surroundings]**
ዙሪያውን አስረዳ ፎቶ አንስቶ በፊትዎ ያለውን ይናገርልዎታል። እሱን ለማድረግ ድምጽ መጨመሪያውን አንዴ ይንኩት።

**[4 — Read Text]**
ጽሑፍ አንብብ ምልክቶችን፣ ሰነዶችንና መለያዎችን ጮክ ብሎ ያነብብልዎታል። ካሜራዎን ወደ የምፈልጉት ጽሑፍ ያድርጉ እና Read Text የምለውን በተን ይንኩ።

**[5 — Ask Question]**
በድምፅ ጥያቄ እንዲጠይቁ ያስችልዎታል፤ ሲያቆሙም በራሱ ይላካል።

**[6 — Volume keys]**
የድምፅ በተኖችንም መጠቀም ይችላሉ፦ የድምፅ መጨመሪያውን ተጭነው ዙሪያዎን ያለውን ነገር ያስረዶዎታል፤ የድምፅ መቀነሻውን ተጭነው ተጨማሪ ጥያቄ ይጠይቁ።

**[7 — Permission bridge]**
Next ስትጫኑ አይኔ የካሜራና የማይክሮፎን ፈቃድ ይጠይቅዎታል። አይኔ እርስዎን እንዲያይና እንዲሰማ ሁለቱንም ይፍቀዱላቸው።

> ⚠️ If you change *any* wording while recording, tell us — the app shows this
> text as the subtitle and speaks it via device TTS as fallback, so the app text
> must be updated to match. This script currently lives in
> `HowToUseScriptFor()` in `app/src/main/java/com/ayne/ui/components/HowToUseGuideDialog.kt`.

## Audio delivery spec

| Item | Requirement |
| --- | --- |
| Format | MP3 (MPEG-1 Layer III), CBR |
| Bitrate / channels | 64–96 kbps, **mono**, 44.1 kHz |
| Duration | 60–90 s, moderate constant loudness, no clipping |
| Checksum | sha256 **of the exact served bytes**, 64 hex chars |

## Technical integration

### How the app consumes this (pipeline)

1. On app launch (and before every guide playback if the clip is missing), the
   app calls the **manifest endpoint** and parses it.
2. For every clip entry it understands (`am/how_to_use` etc.), it checks its
   local cache (`app files/permission-guidance/`). Cache filenames embed the
   manifest `version` + `sha256`, so a changed clip is re-downloaded
   automatically.
3. Uncached clips are downloaded from the clip **endpoint** in the background.
4. When the guide plays: cached clip → `MediaPlayer`. Missing/failed clip →
   device TTS reads the script. **All download failures are silent** — the user
   never sees an error, they just hear the lower-quality TTS voice. That is why
   the contract below must be followed exactly.

Network timeouts on the app side: connect 2 s, read 4 s, total call 6 s. Keep
the manifest small and clips served fast (static files / CDN).

### 1. Manifest endpoint

`GET /api/v1/audio/permission-guidance/manifest` → `200 OK`, `application/json`

```json
{
  "version": "2",
  "supportedLanguages": ["en", "am", "om"],
  "supportedMessages": ["camera", "microphone", "how_to_use"],
  "clips": [
    { "language": "am", "message": "camera",
      "sha256": "<64-hex>",
      "endpoint": "/api/v1/audio/permission-guidance/{language}/{message}" },
    { "language": "am", "message": "microphone",
      "sha256": "<64-hex>",
      "endpoint": "/api/v1/audio/permission-guidance/{language}/{message}" },
    { "language": "am", "message": "how_to_use",
      "sha256": "<64-hex sha256 of the exact MP3 bytes served>",
      "endpoint": "/api/v1/audio/permission-guidance/{language}/{message}" }
  ]
}
```

Field rules (the app enforces all of these):

| Field | Rule |
| --- | --- |
| `version` | string; becomes part of cache filenames — **bump it whenever any clip changes** |
| `supportedLanguages` | array of strings; must contain the clip's `language` |
| `supportedMessages` | array of strings; must contain the clip's `message` — **add `"how_to_use"`** |
| `clips[].language` | one of `en` / `am` / `om` (app-side whitelist) |
| `clips[].message` | one of `camera` / `microphone` / `how_to_use` (app-side whitelist) |
| `clips[].sha256` | 64 hex chars; compared **case-insensitively** against the served bytes |
| `clips[].endpoint` | path with `{language}` and `{message}` placeholders; resolved against the API base URL |

Unknown extra entries are ignored safely, so you can stage future clips/messages
without waiting for app releases.

### 2. Clip endpoint

`GET {base}{endpoint}` with placeholders substituted — e.g.
`/api/v1/audio/permission-guidance/am/how_to_use` — with request header
`Accept: audio/mpeg`.

Hard requirements:

- **HTTP 200** and a non-empty body.
- **`Content-Type` must be exactly `audio/mpeg`** (`audio/mpeg; charset=binary`
  is tolerated — anything else is rejected). Do not serve as
  `application/octet-stream`.
- **Byte-stable**: the bytes served at that URL must hash to the manifest's
  `sha256` at all times. When you re-generate audio, update the manifest
  `sha256` and bump `version` in the same deploy.
- **No auth**: the app sends no credentials on these calls — the endpoints must
  be publicly readable (like the existing camera/microphone clips).
- Idempotent GETs: the app may re-request the same clip several times (launch
  preload + on guide open). Add `Cache-Control` / `ETag` so CDN and OS-level
  HTTP caches can help.

### Backend verification checklist

```bash
BASE=https://<your-api-host>

# 1. Manifest parses and contains the new entry
curl -s "$BASE/api/v1/audio/permission-guidance/manifest" | jq '.supportedMessages, .clips[-1]'

# 2. Clip downloads with the right content type
curl -sI "$BASE/api/v1/audio/permission-guidance/am/how_to_use" | grep -i content-type
#    must print: content-type: audio/mpeg

# 3. Checksum matches the manifest
curl -s "$BASE/api/v1/audio/permission-guidance/am/how_to_use" -o clip.mp3
sha256sum clip.mp3        # compare with the manifest sha256 (case-insensitive)
file clip.mp3             # should report MPEG audio
```

If all three pass, the app will play the recorded voice; anything else silently
falls back to device TTS.

## Phase-2 scripts (reference only — do not record yet)

Content mirrors the approved Amharic script (same instructions, translated).

**English:**
"Welcome to Ayne. Ayne is an AI assistance app that describes your surroundings for you. Ayne has three main actions. Describe Surroundings takes a photo and tells you what is in front of you. To do this, press Volume Up once. Read Text reads signs, documents, and labels out loud. Point your camera at the text you want, and press the button called Read Text. Ask Question lets you speak a question about what you see; it submits automatically when you pause. You can also use the volume buttons: Volume Up describes what is around you, and Volume Down asks follow-up questions. When you press Next, Ayne will ask for camera and microphone permission. Please allow both, so Ayne can see and listen for you."

**Afaan Oromoo:**
"Baga nagaan Ayne dhuftan. Ayne naannoo isinitti ibsu gargaaraa AI dha. Tajaajila gurguddoo sadii qaba. Naannoo Ibsi suuraa kaasee duraatti waan jiru isinitti hima; kana godhuuf Volume+ yeroo tokko dhiibi. Barruu Dubbisi asxaa, barreeffama fi mallattoo dhageessisee dubbisa. Kaameraa keessan gara barruu barbaaddanitti qabadhaa, achis Button Barruu Dubbisi dhiibi. Gaaffii gaafachuuf isin dandeessisa; yeroo dhaabdanis ofumaan ni ergama. Volume irratti fayyadamuu dandeessu: Volume+ dhiibuun waan naannoo keessaniitti jiru isinitti ibsa; Volume gadi dhiibuun gaaffii dabalataa gaafadhaa. Yeroo Next dhiibdu, Ayne kabaja kameraa fi maayikiroofonii gaafata. Ayne isin arguufi isin dhaggeeffachuuf, isaan lachuu hayyamaa."
