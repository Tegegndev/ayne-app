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
  buttons the user will look for: ዙሪያውን አስረዳ / ጽሑፍ አንብብ / ጠይቅ.
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
| Content-Type | **`audio/mpeg`** exactly (charset suffix like `; charset=binary` is tolerated) |
| Duration | 60–90 s, moderate constant loudness, no clipping |
| Checksum | sha256 **of the exact served bytes**, 64 hex chars |

The app rejects the download if the content type differs or the checksum
mismatchs — it will silently fall back to device TTS, which defeats the purpose.

## Manifest changes (`/api/v1/audio/permission-guidance/manifest`)

1. Add `"how_to_use"` to `supportedMessages`.
2. Add the clip entry (same shape as the camera/microphone clips):

```json
{
  "language": "am",
  "message": "how_to_use",
  "sha256": "<64-hex sha256 of the MP3 bytes>",
  "endpoint": "/api/v1/audio/permission-guidance/{language}/{message}"
}
```

3. Serve the file at the endpoint (placeholders substituted). Example URL:
   `/api/v1/audio/permission-guidance/am/how_to_use`
4. Bump the manifest `version` string when you replace clips.

**Scope:** Amharic only for now. English and Afaan Oromoo narrations are
phase 2 — the app needs no changes when you add them (just new clip entries +
the `en`/`om` scripts below, same spec).

## Phase-2 scripts (reference only — do not record yet)

Content mirrors the approved Amharic script (same instructions, translated).

**English:**
"Welcome to Ayne. Ayne is an AI assistance app that describes your surroundings for you. Ayne has three main actions. Describe Surroundings takes a photo and tells you what is in front of you. To do this, press Volume Up once. Read Text reads signs, documents, and labels out loud. Point your camera at the text you want, and press the button called Read Text. Ask Question lets you speak a question about what you see; it submits automatically when you pause. You can also use the volume buttons: Volume Up describes what is around you, and Volume Down asks follow-up questions. When you press Next, Ayne will ask for camera and microphone permission. Please allow both, so Ayne can see and listen for you."

**Afaan Oromoo:**
"Baga nagaan Ayne dhuftan. Ayne naannoo isinitti ibsu gargaaraa AI dha. Tajaajila gurguddoo sadii qaba. Naannoo Ibsi suuraa kaasee duraatti waan jiru isinitti hima; kana godhuuf Volume+ yeroo tokko dhiibi. Barruu Dubbisi asxaa, barreeffama fi mallattoo dhageessisee dubbisa. Kaameraa keessan gara barruu barbaaddanitti qabadhaa, achis Button Barruu Dubbisi dhiibi. Gaaffii gaafachuuf isin dandeessisa; yeroo dhaabdanis ofumaan ni ergama. Volume irratti fayyadamuu dandeessu: Volume+ dhiibuun waan naannoo keessaniitti jiru isinitti ibsa; Volume gadi dhiibuun gaaffii dabalataa gaafadhaa. Yeroo Next dhiibdu, Ayne kabaja kameraa fi maayikiroofonii gaafata. Ayne isin arguufi isin dhaggeeffachuuf, isaan lachuu hayyamaa."
