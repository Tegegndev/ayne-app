# Ayne — Refactor Plan (read this before touching the code)

**Audience:** an AI coding agent, or a human, working in this repository.
**Deadline pressure:** the INSA capstone submission is **Nov 2, 2026, 13:45**. This refactor must be *safe*, not clever.

Read `AGENTS.md`, `README.md` and `ARCHITECTURE.md` first. They describe behaviour you must preserve.

---

## 0. Ground rules (non-negotiable)

1. **Zero behaviour change.** No new features, no UI redesign, no copy rewrites beyond the naming fix in Task 7. If a change would alter what a user hears or sees, it is out of scope.
2. **Preserve the streaming contract.** The read-text and ask flows emit `ocr`/`vision` first, then `audio_chunk`, then `done`. Playback must still start on the **first** chunk. Never buffer the whole audio file before playing.
3. **Preserve zero-upload follow-ups.** When a `sessionId` exists and no new image is captured, the request body carries only `sessionId`/`chatId`. Client history must **not** be sent while a session id is present; that duplication is a deliberate anti-pattern to avoid.
4. **Never touch secrets.** No keys in `app/src/**`. Do not modify `.env` handling or the backend base URL resolution. Do not commit `.env`.
5. **Package and branding must end up as Ayne, not Mirkuz** (Task 7), but do it as the *last* code task, after the structural work is green. Until then, do not rename anything. Never ship a half-renamed tree: either the whole package move compiles and installs, or it stays on its own branch.
6. **No dependency changes.** No upgrades, no new libraries. OkHttp, Retrofit, Compose, CameraX and coroutines are already present; use them.
7. **Tests stay green.** `./gradlew test` must pass after every commit. New helper = new test.
8. **One concern per commit.** Conventional messages, e.g. `refactor(endpoints): add LanguageRouter and delete duplicated path rewriting`.
9. **Stop and ask** if a "fix" would require inventing an endpoint, a response field or a permission. Never guess server behaviour.
10. **This code is reviewed by professionals.** Write it like a senior would, not like a generator. See §0.1 — generated-looking code will be sent back.

---

## 0.1 Professional standards (no generated-looking code)

A reviewer will read this diff. Every rule below exists because the opposite is what makes code read as machine-written.

**Comments**
- Never restate the code. `// increment counter` above `counter++` is noise; a comment must explain *why*, or the constraint the code cannot express.
- No emoji, no decorative separators, no `=====` banners, no "AI: generated this".
- No multi-paragraph explanatory comment blocks above trivial functions. KDoc only on public APIs whose contract is not obvious from the signature.
- Delete narrated change history ("changed from X to Y"). That belongs in the commit message.

**Names**
- No abbreviations that a reader must decode (`mgr`, `svc`, `resp`, `tmp2`).
- Booleans read as predicates (`isPlaying`, `hasSession`); functions read as verbs (`playAudio`, `resetSession`).
- No `data`, `info`, `helper`, `util` as whole class names unless it is genuinely a utility holding pure functions.
- No `Manager` class that owns both HTTP and state: split by responsibility.

**Structure**
- One responsibility per file; one reason to change per class.
- Public functions declare explicit return types.
- Prefer `sealed interface` results over exceptions for expected failures; reserve exceptions for programmer error.
- No `!!`. No `GlobalScope`. No blocking calls on the main thread. No `Thread.sleep`.
- No `lateinit` where a nullable or constructor dependency would do.
- No commented-out code, no dead branches, no unused imports or resources (lint must be clean).
- Magic numbers and strings become constants or resources; every user-facing string, including Amharic, lives in `strings.xml`.

**Consistency**
- Keep the existing style: 4-space indent, trailing commas, expression bodies for single-expression functions. Do not reformat files you did not otherwise change.
- One HTTP stack (OkHttp), one JSON approach (keep what is there), one coroutine scope convention (`viewModelScope`).
- Logging: `android.util.Log` with a stable tag per class. No `println`. No logging of image or audio payloads.

**Diff hygiene**
- A reviewer must be able to read each commit in under five minutes.
- Move-only commits are pure moves: no formatting changes, no renames, no fixes bundled in.
- If a rename touches more than twenty files, split it from behaviour changes.

**What "done" looks like to a reviewer:** the diff makes the codebase smaller and clearer, every new abstraction eliminates a real duplication, and nothing was added because it seemed impressive.

---

## 1. Extract language/endpoint routing

**Problem:** the same nested `when (preferredLanguage)` block with regex path rewriting is duplicated in `VisionApiService.seeAndDescribe()`, `VisionApiService.seeAndDescribeStream()` and the repository. Each copy is a bug waiting to diverge.

**Do:**
- Add `data/endpoints/LanguageRouter.kt` with a single pure function:
  ```kotlin
  fun resolve(baseUrl: String, language: String, path: EndpointPath, variant: Variant = Variant.PLAIN): String
  ```
  where `EndpointPath` covers `SEE_AND_DESCRIBE`, `READ_TEXT`, `ASK_QUESTION`, `READ_TEXT_AND_SIGNS`, and `Variant` covers `PLAIN`, `STREAM`, `AUDIO_STREAM`.
- Rules to encode: `am`/`om` insert the language segment; `en` (or unknown) omits it; `stream`/`audio-stream` append after the language segment; a URL that already matches must be returned unchanged (idempotent).
- Replace all three call sites. Delete the regex copies.
- **Test:** `LanguageRouterTest` with a table covering `am`, `om`, `en`, unknown language, base URL with a trailing slash, base URL already containing the language, and already-streaming URLs.

**Acceptance:** `grep -rn "see-and-describe(?:/(?:am|om))?" app/src/main` returns nothing.

---

## 2. Split `MainActivity.kt`

**Problem:** 3,648 lines containing the Activity, `AyneViewModel`, `AyneUiState`, ~10 composables and a fallback-answer generator.

**Target layout:**
```
ui/MainScreen.kt                  AyneAppScreen, AyneScreenContent
ui/components/AyneTopBar.kt       AyneTopBar, LanguageSelector
ui/components/AyneActionCards.kt  AyneActionCard
ui/components/AyneBottomBar.kt    AyneBottomControls
ui/components/AskQuestionPanel.kt AyneAskQuestionPanel
ui/components/CompanionPanel.kt   AyneCompanionPanel
ui/components/VisionSettingsDialog.kt
viewmodel/AyneViewModel.kt        AyneViewModel + AyneUiState
domain/CompanionFallback.kt       generateCompanionFallbackAnswer()
MainActivity.kt                   edge-to-edge + setContent only
```
- Move code, do not rewrite it. Keep every `contentDescription`, every haptic call, every spoken cue exactly as-is.
- Keep `AyneUiState` a single immutable data class; do not introduce event/effect plumbing in this task.
- Public composables keep their names so previews and call sites compile unchanged.
- **Acceptance:** `MainActivity.kt` under 120 lines; no file over 800 lines; app builds and every screen still renders.

---

## 3. Make failures audible

**Problem:** the audio path swallows exceptions: `catch (_: Exception) {}`. A blind user gets silence with no explanation.

**Do:**
- Replace each silent catch in `MirkuzVisionRepository`, `AudioPlaybackHelper` and `Pcm16Decoder` with a typed failure (`Result<T>` or a sealed `AudioFailure`) propagated to the ViewModel.
- In the ViewModel, map failures to a spoken message through the existing speech/feedback path. First implement `error_audio_playback`, `error_network`, `error_bad_response` in `values/strings.xml` and `values-am/strings.xml`.
- Never leave a state transition that produces neither speech nor haptic feedback.

**Acceptance:** `grep -rn "catch (_: Exception) {}" app/src/main` returns nothing (or each remaining hit carries a comment explaining why it is deliberate).

---

## 4. Temp file hygiene

**Problem:** `File.createTempFile(...)` per turn, never deleted, in two places.

**Do:** a single `util/TempAudioStore` that creates files under `cacheDir/audio`, deletes on playback completion and on `onStop`/`onDestroy`, and caps retained files (e.g. 3). Replace both creation sites.

**Acceptance:** after ten ask questions, `cacheDir/audio` holds no more than three files.

---

## 5. Retry and offline behaviour

**Do:** one retry with backoff (e.g. 400 ms, then 1.2 s) for `seeAndDescribe` and `askQuestion` on `IOException` and 5xx only. No retry on 4xx. Spoken fallback when all attempts fail. Reuse one `OkHttpClient`; never build one per request.

**Acceptance:** airplane-mode request produces one spoken error, not a hang; no duplicate requests on success.

---

## 6. Consolidate duplicate HTTP clients

`VisionApiService`, `MirkuzVisionRepository`, `VisualSessionManager` and `MirkuzVisionManager` each construct their own `OkHttpClient` and some duplicate the same request-building logic. Extract one `data/HttpClientProvider` and one request builder; keep timeouts as they are unless a task explicitly changes them.

---

## 7. Package name and branding: Mirkuz → Ayne

This is the one task that can destabilise the build, so it runs last and lands as a single commit (or two: package move, then copy).

### 7a. Code identity

Current state, verified: `namespace = "com.example"` in `app/build.gradle.kts`, sources under `app/src/main/java/com/example/` and `app/src/main/java/com/mirkuz/vision/`. No `google-services.json` is committed, so nothing here is pinned by Firebase yet.

Target:
| Setting | Target value |
| --- | --- |
| `namespace` | `com.ayne` |
| `applicationId` | `com.ayne.app` |
| Kotlin packages | `com.ayne.*` (`data`, `ui`, `viewmodel`, `domain`, `util`) |
| Test packages | `com.ayne.*` under `app/src/test` and `app/src/androidTest` |

Steps, in this order:

1. Use Android Studio's **Refactor → Rename** on the package (or `git mv` the directories and rewrite `package`/`import` lines). Do not hand-edit a few files and leave the rest.
2. Rename Mirkuz-prefixed classes as part of the same move:
   - `MirkuzVisionRepository` → `VisionRepository`
   - `MirkuzVisionManager` → `VisionNetworkManager`
   - `MirkuzVisionManagerTest` → `VisionNetworkManagerTest`
   - `MirkuzVisionRequest` / `MirkuzVisionResponse` → `AyneRequest` / `AyneResponse`
   - temp-file prefixes `mirkuz_*` → `ayne_*`
3. Update `namespace` and `applicationId` in `app/build.gradle.kts`. Leave `minSdk`, `targetSdk` and every dependency untouched.
4. Search the whole tree for `com.example`, `com.mirkuz`, `Mirkuz`, `mirkuz` and resolve every hit, including ProGuard/R8 rules, test sources, `README`/`ARCHITECTURE` mentions, and `strings.xml`.
5. If Firebase is configured later, register `com.ayne.app` in the Firebase console before adding `google-services.json`. Never invent a `google-services.json` and never commit one that does not match `applicationId`.
6. Expect the new build to install as a **separate app**. Existing installs under the old id do not migrate; note that in the PR description.

### 7b. Branding and copy

- Replace every user-visible "Mirkuz Vision" with "Ayne" in `values/strings.xml`, `values-am/strings.xml`, accessibility labels, spoken text and dialogs.
- Keep the legal/credit wording ("Ayne, built by Tegegn Wukianos Tafese").
- Do not touch the brand mark assets delivered in the branding PR.

### Acceptance

- `grep -rniE "mirkuz|com\.example" app/ docs/ README.md` returns nothing outside this plan and git history.
- `./gradlew lint test assembleDebug` green.
- Fresh install on a clean device shows "Ayne" in the launcher, the recents card and app info.

## 8. Documentation upkeep

- `ARCHITECTURE.md` §8 "Known limitations": strike through or delete the items resolved by Tasks 1–6, keep the rest honest.
- `README.md` "Repository layout": update to the new package structure.

---

## 9. Definition of done

```
./gradlew lint test assembleDebug
```
must all pass, plus a manual run of the five flows on a device:

1. Describe surroundings
2. Read text (printed page and a Birr note)
3. Ask question with a new capture
4. Follow-up with volume-down on the same session
5. Repeat last answer

Report: files moved, before/after line counts for the five largest files, tests added, and anything deliberately left unchanged.

---

## Out of scope

Continuous video streaming, offline speech models, new languages, location features, UI refresh, Play Store prep, dependency upgrades, package renames, backend changes.
