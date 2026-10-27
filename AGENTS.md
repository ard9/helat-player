# AGENTS.md

Guide for coding agents (and people) working on Lingua Player. Read this before changing code.
User-facing documentation is in `README.md`.

## What the app is

An Android video player for language learning. The owner watches films (mostly MKV files and
direct online links, often HEVC 10-bit with Persian and English subtitle tracks) and saves
spoken lines as flashcards: audio clip + video frame + text + meaning, reviewed with a
5-box Leitner system.

The UI language is English. The owner talks to agents in Persian; reply in the language they use.

## Working agreement

These rules come from the owner and apply to every change:

1. **Agree before building.** For anything non-trivial, restate the task, ask only questions
   that change the result (with a suggested default), outline the plan, and wait for approval.
2. **Build only what was agreed.** No speculative options, flags or abstractions. Error
   handling, input validation and secure defaults are part of the job, not extras.
3. **Ideas are proposals.** Describe an improvement (benefit and cost) and implement it only
   after approval.
4. **Always flag security issues**, in new or existing code: what, where, impact, severity
   (Critical/High/Medium/Low, honestly), and the proposed fix. Do not change existing code to
   fix them without approval.
5. **All file content is English**: code, comments, docs, commit messages, UI strings.
6. **Commits**: authored as `ard9 <ard9@users.noreply.github.com>`. Do **not** add
   `Co-Authored-By`, session links or other tool trailers; the owner asked for them removed.

## Build, run, verify

There is no Gradle wrapper and no local Android SDK is assumed. **GitHub Actions is the build.**

- `.github/workflows/build.yml` runs on every push to `main` (JDK 17, Gradle 8.13):
  first **unit tests** (`gradle testDebugUnitTest`), then the APK build. A failing test stops
  the build, so no broken APK is published.
- If the signing secrets exist it builds a signed `release` APK, otherwise a `debug` APK.
- Every run publishes a GitHub Release `build-<run number>` with `LinguaPlayer.apk`.
  The owner installs from the release page on the phone.
- On failure, the step "Summarize build errors" puts compiler errors and failed tests (with
  their assertion messages) in a check-run annotation titled **Build errors**; the HTML test
  report is uploaded as the `test-report` artifact. Read the annotation with:
  ```sh
  JOB=$(gh api repos/ard9/lingua-player/actions/runs/<RUN_ID>/jobs --jq '.jobs[0].id')
  gh api repos/ard9/lingua-player/check-runs/$JOB/annotations --jq '.[].message'
  ```
- Raw job logs may not be downloadable from agent sandboxes; use the annotation.
- Agents usually cannot run the app. Say clearly what was verified (compiles, logic tested
  in a prototype) and what was not (behaviour on the phone).

Signing: four repository secrets (`SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`,
`SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`). The keystore is never committed (`.gitignore`
blocks `*.jks`, `*.keystore`, `signing-secrets*.txt`). Changing the key forces users to
uninstall and lose their cards, so never rotate it casually.

Toolchain pins that matter (libVLC 3.7 requires them): AGP 8.11.1, Kotlin 2.2.10,
`compileSdk 36`, `targetSdk 35`, `minSdk 26`. APK is **arm64-v8a only** to limit size.

## Code map

Package `io.github.ard9.linguaplayer` (`app/src/main/java/...`):

| Path | Responsibility |
| --- | --- |
| `LinguaApp.kt` | App singletons: repositories, `ClipService`, `SpeechFinder`, libVLC instances (one per subtitle style, reference counted). |
| `MainActivity.kt` | "Open with" intents, theme, simple state-based navigation (`Screen`), http warning dialog. |
| `data/` | `Card` + `Leitner` scheduling, `CardRepository` (JSON file + clip/image files), `CardBackup` (ZIP export/import, treats imports as untrusted), `RecentRepository` (+ resume positions, `ResumePolicy`), `SettingsRepository` (subtitle style, clip margins). |
| `player/` | `VlcMedia` opens content:// via file descriptor or http(s) URL for libVLC; `FrameGrabber` copies the video surface (PixelCopy) for card pictures. |
| `ui/PlayerScreen.kt` | The player: libVLC + `VLCVideoLayout`, controls, rotation/full screen, quick save button, save editor, line finding. The largest and most central file. |
| `ui/` (others) | Home, cards list (`BoxScreen`), card detail, review, dialogs, theme ("cinema" palette, Atkinson Hyperlegible + Archivo fonts). |
| `subtitle/SubtitleParser.kt` | External SRT/VTT parsing; `SubtitleLookup` (which line belongs to a moment, overlapping translation). |
| `subtitle/mkv/` | Reads built-in subtitle tracks from MKV/WebM: `ByteSource` (local fd or HTTP Range), `EbmlReader` (streaming EBML with byte budget), `MkvSubtitles` (headers, cues, clusters around a moment, ASS/SRT/WebVTT text cleanup). |
| `clip/` | Audio cutting. `ClipService` tries Media3 `TransformerClipper` (m4a) and falls back to `VlcClipper` (libVLC sout → wav) + `WavTrim`. |
| `speech/` | No-subtitle fallback: `SpeechFinder` cuts 8 s before / 6 s after the moment, `SileroVad` (ONNX Runtime, model in `assets/silero_vad.onnx`; `Stream` for live audio), `SentenceFinder` (pure logic: hysteresis, gap merge, split long speech). |
| `roleplay/` | Role-play (user and actor take turns): `RolePlayLogic` (roles, `SpeechEndDetector`, `WavWriter`; pure), `VoiceRecorder` (mic → WAV, stops on silence), `RolePlaySession` (drives the player through a `ScenePlayer` interface: mute/pause/seek, records, plays back). UI in `ui/RolePlayUi.kt`; scene lines from `MkvSubtitles.linesFrom` or `SubtitleLookup.scene`. |

## How saving a line works

Entry points in `PlayerScreen`: **tap** the bookmark button → `quickSave()` (background, film
keeps playing, Undo notice); **hold** → `openEditor()` (pauses, `CardEditorDialog`).
Both call `findLine(now)`, which tries, in order:

1. **External subtitle file** loaded by the user → exact times and text.
2. **Built-in MKV/WebM text track** → `MkvSubtitles.linesAround(now)` reads only ~12 s of the
   file before the moment (HTTP Range for links). Text comes from the preferred non-Persian
   track (the one shown in VLC if it can be matched, else default, else first); a Persian track
   fills the meaning. The editor offers "Text from" chips to switch tracks.
3. **Speech detection (VAD)** → pauses around the sentence.

The found range is widened by the user's **clip margins** (default 0.45 s each side), then
`ClipService` cuts the audio and the card is stored in Box 1.

## Decisions and lessons (do not undo without a reason)

- **Playback is libVLC, not Media3.** Media3 failed with `DECODING_FAILED` on HEVC 10-bit
  (`hvc1.2.4.L120.90`) on the owner's phone; libVLC plays it with software decoding.
  Media3 is kept only for clipping and for playing saved clips.
- **Built-in subtitles are read from the file, not the screen.** An earlier version watched
  libVLC's subtitle surface and used OCR (ML Kit). It failed: with software decoding libVLC
  blends subtitles into the video surface, so there is no separate layer. Removed, with ML Kit.
- **libVLC may ignore `:start-time`** on some files/links and write audio from 0 to the end
  time. `VlcClipper` therefore also stops on `TimeChanged` past the end, and `WavTrim` keeps
  only the last requested duration. `WavReader` is tolerant (any channels/rate, unset sizes).
- **`StartGate`**: playback starts only after both the media is set and the video view is
  attached; otherwise the video was sometimes black while audio played.
- **Edge-to-edge** (targetSdk 35): player controls use `WindowInsets.safeDrawing` padding so
  system navigation buttons do not cover them.
- **Subtitle style** (colour/size) is applied to libVLC at instance creation, so a style change
  takes effect for built-in tracks the next time a video is opened.

## Tests

JVM unit tests live in `app/src/test/` (JUnit 4; the real `org.json` is on the test classpath
because Android's is a stub). They cover the logic that can run without a device:

| Test | Covers |
| --- | --- |
| `subtitle/mkv/MkvSubtitlesTest` | Reading built-in tracks from real MKV/WebM files: track info, line times and text, tags, a line from an earlier cluster, cues at front/back/none, WebVTT, jumping to the right part of the file, non-Matroska and truncated input. |
| `subtitle/mkv/SubtitleTextCleanerTest` | ASS fields/tags/drawings, SRT tags and entities. |
| `subtitle/SubtitleParserTest` | SRT/VTT parsing, encodings; `SubtitleLookup` (which line a tap belongs to, translations). |
| `speech/SentenceFinderTest` | Sentence boundaries from speech probabilities (pauses, short gaps, long speech, noise). |
| `clip/WavTest` | `WavTrim` (VLC start-time bug) and `WavReader` (downmix, resample, limits). |
| `data/CardTest` | Leitner scheduling, card JSON round trip and loading cards from older versions, clock text. |
| `data/CardBackupTest` | Backup round trip, skipping existing cards, and hostile backups (paths outside the folders, overwriting files, other ZIPs, damaged or newer data). |
| `data/ResumePolicyTest` | Which playback positions are worth resuming. |
| `roleplay/RolePlayLogicTest` | Alternating roles, end-of-speech detection, WAV writing. |

Fixtures are in `app/src/test/resources/media/` (a few small files) and are recreated with
`tools/make-test-media.sh` (needs ffmpeg). Their subtitle lines are listed in `MkvSubtitlesTest`.

Rules for changes:

- Change logic in these areas together with its tests; add a test for every bug fixed there.
- Keep logic that can be tested out of Android classes (as `SentenceFinder`, `MkvSubtitles`,
  `WavTrim` are), so it can be covered here.
- Not covered by tests: libVLC playback, UI, Media3/VLC clipping, the Silero model itself.
  For those, say exactly what was and was not verified on a device.

## Data on the device

App-private storage only, excluded from backup (`allowBackup=false`, data extraction rules):

- `files/cards.json` — list of `Card` JSON objects. New fields must be optional when reading
  (`optString`, null checks) so old cards still load. A corrupt file is renamed, never overwritten.
- `files/clips/<id>.m4a|wav`, `files/images/<id>.jpg`.
- SharedPreferences `settings` and `recents` (recent sources and resume positions).
- Backups (user-chosen location): ZIP with `cards.json` (`{"format":1,"cards":[...]}`) first,
  then `clips/` and `images/`. Bump `CardBackup.FORMAT` only for incompatible changes.

## Security notes

- Permissions: `INTERNET`, and `RECORD_AUDIO` (asked at runtime, only when role-play starts).
  Recordings are written to `cacheDir/roleplay/…` and deleted when the session is closed.
  No analytics, no own server.
- `usesCleartextTraffic=true` so http streams play; the app warns before opening any http link.
- Stream URLs (may contain tokens) are stored in recents and cards; hence no Android backup.
  The user's own card backup includes `sourceUri`; the app tells them to keep it private.
- Untrusted input: media files and network data. Size limits exist everywhere they are read
  (subtitle file 5 MB, EBML element/block caps, HTTP read budget 64 MB, WAV handling).
  Keep such limits when adding parsers.
- libVLC parses untrusted media in native code; keep it updated.
- Known open item: GitHub Actions are pinned by tag (`@v4`), not by commit SHA.

## Known limitations

- MP4 built-in subtitles are not read (only MKV/WebM); picture subtitles (PGS/VobSub) cannot be
  read as text. Both fall back to VAD.
- VAD cannot separate sentences spoken without a pause.
- APK is large (~80 MB): libVLC, ONNX Runtime and Media3 native code.
