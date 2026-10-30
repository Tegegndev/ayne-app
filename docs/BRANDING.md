# Ayne — Branding

## The mark

An eye whose iris is a camera aperture. It carries both halves of the product in one
shape: the eye is what the user cannot rely on, the aperture is what the app sees with.
The name does the rest — "Ayne" means *eye*.

No letters, no Ge'ez script inside the mark. Text lockups are set separately in a type
face, which keeps the icon legible at 48px and avoids rendered-script artefacts.

## Palette

| Role | Hex | Notes |
| --- | --- | --- |
| Background | `#000000` | Pure black. Never lighten for the icon or app chrome. |
| Primary accent | `#FFD600` | The mark, headings, primary buttons. |
| Text on black | `#FFFFFF` | Body copy and secondary labels. |
| Text on accent | `#000000` | Only on `#FFD600` fills. |

All pairings clear WCAG AAA for large text and AA for body text. Do not introduce a third
hue in product surfaces; the visual language is two colours, deliberately.

## Files

| Path | Purpose |
| --- | --- |
| `assets/logo/ayne-mark.svg` | Master vector, black background, use for docs and web |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | Adaptive icon foreground (72dp safe zone) |
| `app/src/main/res/drawable/ic_launcher_monochrome.xml` | Themed-icon silhouette (Android 13+) |
| `app/src/main/res/drawable/ic_launcher_background.xml` | Solid black adaptive background |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher*.xml` | Adaptive icon manifest entries |
| `app/src/main/res/mipmap-anydpi-v33/ic_launcher*.xml` | Same, plus monochrome layer |

## Icon rules

- Keep the symbol inside the central 72dp of the 108dp canvas. Launchers mask to circles,
  squircles and rounded squares; anything outside the safe zone gets clipped.
- Minimum legible size is 48px. Below that, use the mark alone with no surrounding detail.
- Never add gradients, drop shadows or outlines. Two colours, flat fills, round caps.
- On light surfaces (rare), invert to black mark on `#FFD600`; never introduce a new hue.

## Wordmark

"Ayne" set in a bold geometric sans (Inter Bold, Poppins SemiBold) in `#FFD600`, vertically
centred with the mark, with a gap equal to roughly half the mark's width. Amharic (አይን) may
appear as a secondary line in Noto Sans Ethiopic — never inside the icon.

## Voice

Short, plain sentences spoken aloud. Name the thing, say where it is, stop. No filler,
no "as an AI", no exclamation marks. The user is listening, often in public, and cannot skim.
