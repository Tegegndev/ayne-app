# Ayne (አይን)

**A voice-first AI visual assistant for blind and low-vision people in Ethiopia.**

Ayne turns a phone camera into a pair of eyes. Point it at the world, ask in Amharic or Afaan Oromo, and hear the answer spoken back — no screen reading required.

Built as the graduation capstone for the 5th round INSA Summer Camp, Software Development track.

---

## Why Ayne exists

More than 1.5 million people in Ethiopia live with blindness or low vision. Every mainstream visual-assistance app — Seeing AI, Be My Eyes, Envision — is English-first. None of them read Ethiopian Birr notes, understand Ge'ez script, or speak Amharic or Afaan Oromo.

That leaves millions of people locked out of tools the rest of the world already takes for granted. Ayne is built for them first: voice in, voice out, in their own language.

## What Ayne can do

| Feature | What it means for the user |
| --- | --- |
| **Describe Surroundings** | One press, and the phone describes the scene aloud — objects, layout, and spatial cues ("door, slightly left, about two meters ahead"). |
| **Read Text** | Reads books, notices, banknotes and prescription labels out loud, word for word. If the photo is blurry, Ayne asks for a retake. |
| **Ask a Question** | Ask anything about what is in front of the camera — "what colour is this shirt?", "is this my medicine?" — and hear the answer. |
| **Follow-up questions** | Keep asking about the same scene without pointing the camera again. |
| **Repeat last answer** | One big button replays the last answer — because hearing something twice is normal. |
| **Three languages** | Amharic, Afaan Oromo, and English fallback — with several spoken voices to choose from for each. |

## Using Ayne

1. On first launch, choose your language. Ayne greets you in it.
2. Point the phone at whatever you want to know about.
3. Press **volume-up** to capture the scene and ask your question by voice.
4. The answer is spoken aloud — usually within a few seconds.
5. Press **volume-down** to ask a follow-up about the same scene.
6. Long-press the volume keys any time to use them normally.

Everything is driven by voice and hardware keys, so the app works without ever looking at the screen.

## Designed for Ethiopian realities

- **Data-friendly.** Ayne captures one photo per question instead of streaming video — a full interaction costs a few hundred kilobytes, not megabytes.
- **Fast answers.** The reply starts playing as soon as the first piece is ready, instead of waiting for the whole thing.
- **Smart follow-ups.** The scene is remembered on the server, so follow-up questions send almost no data.

## Privacy and safety

- No account, no sign-in, no tracking.
- Only what is needed to answer you leaves the phone: the photo and your spoken question. Nothing else is stored.
- The app's service holds the AI credentials — your phone never carries them.
- **Ayne is a scene describer and reader, not a navigation device.** It does not replace a cane, a guide dog, or a sighted companion.

## Accessibility

Accessibility is the product, not a checkbox:

- **Voice-first.** Every action produces spoken feedback; the screen is a convenience, never a requirement.
- **TalkBack compatible.** Every control is labelled and comfortably large.
- **Hardware keys.** The core loop runs on volume-up / volume-down, by feel.
- **High contrast.** Pure black background with yellow and white text, above WCAG AAA thresholds.
- **Interruption.** Any new input stops the current speech immediately rather than queuing behind it.

## Roadmap

- **Real-time voice chat** — a live, continuous conversation with the assistant instead of one question at a time.
- **Automatic flashlight** — when the camera detects dim light or darkness, the flashlight turns on by itself so descriptions stay accurate at night.
- **Daily news** — hear the day's news read aloud in Amharic or Afaan Oromo.
- **Sports news** — scores and updates on demand, spoken in your language.
- **Location awareness** — "where am I?" answered with nearby landmarks.
- **Offline speech** — voice input that works without connectivity.
- **More Ethiopian languages** — Sidama, Tigrinya and Wolaytta.
- **iOS version** — same experience on iPhone.

## Getting the app

Download the latest installer from the [GitHub Releases](https://github.com/Tegegndev/ayne-app/releases) page and install it on the phone — no account needed. The app requests camera and microphone permission on first use; both are required.

## Credits

- Built by **Tegegn Wukianos Tafese**, IT student at Wolaita Sodo University.
- Vision reasoning: **Google Gemini**.
- Amharic and Afaan Oromo voices: **Addis AI**.
- Speech data for planned offline models: **Afrivoice Ethiopia** by Digital Umuganda (CC BY 4.0).

## License

MIT — see [LICENSE](LICENSE).
