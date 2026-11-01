# How-to-Use Guide Screen — Design

Date: 2026-11-01
Status: Approved

## Goal

Give blind/low-vision users a spoken guide to Ayne's core actions, shown once on
first launch (after language selection, before permission prompts) and
re-playable on demand from the top bar.

## Decisions

| Question | Decision |
| --- | --- |
| Entry points | First launch **and** on-demand via a help icon in `AyneTopBar` |
| Form | Full-guide screen: one backend-generated audio clip played start-to-finish, with the script shown as on-screen subtitle text |
| Content | The 3 main actions (Describe Surroundings, Read Text, Ask Question) + volume-key controls |
| First-launch order | Language picker → guide → permission prompts (guide's last line announces that permission dialogs are next) |
| Narration audio | Backend-generated clip, **Amharic only** at launch; English/Afaan Oromoo fall back to device TTS |
| Controls | `Next` (stop audio, close) and a `Replay` icon (restart audio) |

## Architecture

### Backend contract

One new message key in the existing permission-guidance manifest
(`/api/v1/audio/permission-guidance/manifest`):

```
message: "how_to_use", language: "am"   // "en", "om" may be added later
```

Same shape as the camera/microphone clips: endpoint + sha256. The app-side
`preload()` message filter is widened to accept `how_to_use`. Adding `en`/`om`
clips later requires zero app changes (manifest-driven).

### New composable: `HowToUseGuideDialog`

Full-screen `Dialog` in `com.ayne.ui.components`, same visual family as
`FirstLaunchLanguagePickerDialog` (black background, yellow accents, large
TalkBack-friendly buttons with English content-description hints).

- Opens → plays the cached `how_to_use` clip; if not cached, device TTS reads
  the script in the selected language. Never blocks on network.
- Subtitle: full script text, scrollable, trilingual (Kotlin-side
  `when (selectedLanguage)`, matching the `capturedMsg` pattern — there is no
  `values-om` resource folder).
- `Next` button: `PermissionPromptSpeaker.stop()` then close.
- `Replay` icon: `stop()` then play again from the start.

### Wiring (`AyneAppScreen`)

- `showHowToUse` state. First-launch `LaunchedEffect`: after the picker closes,
  show the guide and `snapshotFlow { showHowToUse }.first { it == false }`
  before continuing to permissions (same wait pattern as the picker).
- Top bar help icon sets `showHowToUse = true` from any screen/language.
- `onHelpClick` threaded through `EnglishMockScreen`, `AfaanOromooMockScreen`,
  `AyneScreenContent` → `AyneTopBar`.

### Audio playback

`PermissionPromptSpeaker` gains a `playGuide(text, languageCode, message,
baseUrl)` method: cached clip → MediaPlayer (as today); missing clip → device
TTS fallback. `stop()` (added with the welcome-narration fix) interrupts both.

## Honest caveat

On a true first install the clip likely hasn't downloaded when the guide opens
(manifest races the UI), so first-launch users may hear device TTS Amharic.
Returning users replaying from the top bar get the custom recorded voice. This
was accepted deliberately to keep the screen simple — no waiting state.

## Testing

- Unit: manifest filter accepts `how_to_use`; clip-cache fallback logic.
- Instrumentation/manual: first launch order (picker → guide → permissions),
  top-bar replay, Next/Replay interruption, TalkBack traversal order,
  trilingual subtitle + fallback TTS text.
