# Architecture

Kotlin, Jetpack Compose (Material 3), one activity. Package `io.github.ard9.linguaplayer`
(`app/src/main/java/...`).

## Code map

| Path | Responsibility | Details |
| --- | --- | --- |
| `LinguaApp.kt` | App singletons: repositories, `ClipService`, `SpeechFinder`, libVLC instances (one per subtitle style, reference counted). | `player.md` |
| `MainActivity.kt` | "Open with" intents, app language (`attachBaseContext`), theme, state-based navigation (`Screen`), http warning dialog, backup/restore calls. | `i18n.md` |
| `data/` | `Card` + `Leitner`, `CardRepository` (JSON + clip/image files), `CardBackup` (ZIP), `RecentRepository` (+ resume positions, `ResumePolicy`), `SettingsRepository` (language, subtitle style, clip margins, role-play choices, welcome card), `PlaybackSource`. | `cards-and-review.md` |
| `player/` | `VlcMedia` opens content:// via file descriptor or http(s) URL for libVLC; `FrameGrabber` copies the video surface (PixelCopy) for card pictures; `LineLoop` decides when a loop jumps back; `LineSearch` picks the line for a moment (track order, margins, meaning, duplicates). | `player.md`, `saving-lines.md` |
| `ui/PlayerScreen.kt` | The player: libVLC + `VLCVideoLayout`, controls, rotation, quick save, editor, line finding, repeat/loop, role-play hookup. Largest file. | `player.md`, `saving-lines.md` |
| `ui/RolePlayUi.kt` | Role-play setup dialog and overlay. | `roleplay.md` |
| `ui/` (others) | Home (welcome card), `BoxScreen`, `CardScreen`, `ReviewScreen`, `SettingsScreen`, `GuideScreen`, dialogs, `Theme` (cinema palette, fonts), `PlayerIcons`. | |
| `subtitle/SubtitleParser.kt` | External SRT/VTT parsing; `SubtitleLookup` (line for a moment, overlapping translation, scenes). | `saving-lines.md` |
| `subtitle/mkv/` | Built-in MKV/WebM subtitle tracks: `ByteSource`, `EbmlReader`, `MkvSubtitles` (lines around a moment, scenes, all lines), text cleanup. | `saving-lines.md` |
| `subtitle/SubtitleSearch.kt`, `SubtitleIndexCache.kt` | Searching a film's lines; caching all lines of a film. | `player.md` |
| `text/TextMatch.kt` | Loose text matching and highlights, shared by card and subtitle search. | `player.md` |
| `clip/` | Audio cutting: `ClipService` → `TransformerClipper` (Media3, m4a), fallback `VlcClipper` + `WavTrim`. | `saving-lines.md` |
| `speech/` | No-subtitle fallback: `SpeechFinder`, `SileroVad`, `SentenceFinder`, `WavReader`. | `saving-lines.md` |
| `roleplay/` | `RolePlayLogic` (pure), `VoiceRecorder`, `RolePlaySession`. | `roleplay.md` |
| `reminder/` | Daily review reminder: `ReminderTime` (pure), `ReviewReminder` + `ReminderWorker` (WorkManager, notification). | `cards-and-review.md` |
| `AppLocale.kt` | `withLanguage` / `inAppLanguage`: a context in the chosen language (activity and notifications). | `i18n.md` |
| `res/values*/strings.xml` | All UI text, English and Persian. | `i18n.md` |
| `assets/silero_vad.onnx` | Speech detection model (MIT). | |
| `res/font/` | Atkinson Hyperlegible, Archivo Condensed, Vazirmatn (OFL, see `licenses/`). | |

## Navigation

`MainActivity.AppNavigation` keeps a `Screen` in state: Home, Player, Box, CardDetail, Review,
Settings, Guide. Back goes to the parent screen. Settings and Guide are saved across activity
recreation (language change); other screens return to Home.

## Data on the device

App-private storage only, excluded from Android backup (`allowBackup=false`, data extraction
rules), because stream URLs may contain tokens:

- `files/cards.json`: list of `Card` JSON objects. New fields must be optional when reading
  (`optString`, null checks) so old cards still load. A corrupt file is renamed, never overwritten.
- `files/clips/<id>.m4a|wav`, `files/images/<id>.jpg`.
- SharedPreferences `settings` (language, subtitle style, clip margins, role-play, welcome
  card) and `recents` (recent sources and resume positions).
- `cacheDir/roleplay/…`: role-play recordings, deleted when the session closes.

## Where logic should live

Logic that can run without Android goes in plain classes with unit tests (as `SentenceFinder`,
`MkvSubtitles`, `WavTrim`, `RolePlayLogic` are). UI files call them.

`PlayerScreen.kt` keeps the wiring (reading files, libVLC, state, UI); the decisions are in
tested classes: `LineSearch` (which line), `LineLoop` (loop jumps), `SceneRange` and `Roles`
(role-play), `SpeechFinder`/`SentenceFinder` (no subtitles). When adding player logic, put the
decision in such a class first.
