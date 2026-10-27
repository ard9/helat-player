# Testing

JVM unit tests live in `app/src/test/` (JUnit 4). The real `org.json` is on the test
classpath because Android's is a stub; `testOptions` returns default values for other Android
calls. CI runs them before every build (`gradle testDebugUnitTest`); there is no local SDK.

## Tests

| Test | Covers |
| --- | --- |
| `subtitle/mkv/MkvSubtitlesTest` | Built-in tracks from real MKV/WebM files: track info and labels (also in Persian), line times and text, tags, a line from an earlier cluster, cues at front/back/none, WebVTT, jumping to the right part of the file, scenes by count and by time, non-Matroska and truncated input. |
| `subtitle/mkv/SubtitleTextCleanerTest` | ASS fields/tags/drawings, SRT tags and entities. |
| `subtitle/SubtitleParserTest` | SRT/VTT parsing, encodings; `SubtitleLookup` (which line a tap belongs to, translations, scenes). |
| `speech/SentenceFinderTest` | Sentence boundaries from speech probabilities (pauses, short gaps, long speech, noise). |
| `clip/WavTest` | `WavTrim` (VLC start-time bug) and `WavReader` (downmix, resample, limits). |
| `data/CardTest` | Leitner scheduling, card JSON round trip and older cards, clock text (ASCII also on a Persian phone). |
| `data/CardSearchTest` | Card search: words, case, meaning and film, Persian letter forms. |
| `data/CardBackupTest` | Backup round trip, skipping existing cards, hostile backups (paths outside the folders, overwriting files, other ZIPs, damaged or newer data). |
| `player/LineLoopTest` | Loop jumps: waiting for the old position after a jump, repeating, early landing, giving up waiting, never ending by itself. |
| `data/ResumePolicyTest` | Which playback positions are worth resuming. |
| `roleplay/RolePlayLogicTest` | Alternating roles, scene time range checks, end-of-speech detection, where speech began, WAV writing. |
| `StringsTranslationTest` | Every English string has a Persian translation with the same placeholders, and no stale ones. |
| `DocsTest` | Every doc named in `AGENTS.md` exists, and no doc is longer than 200 lines. |

## Fixtures

`app/src/test/resources/media/`: `subs.mkv`, `cues-front.mkv`, `no-cues.mkv`, `subs.webm`.
Recreate with `tools/make-test-media.sh` (needs ffmpeg). Their subtitle lines are listed in
`MkvSubtitlesTest`.

## Rules

- Change logic in a tested area together with its tests; add a test for every bug fixed.
- Keep logic that can be tested out of Android classes, so it can be covered here.
- Not covered: libVLC playback (the loop's jumps themselves), UI, Media3/VLC clipping, the Silero model itself, microphone
  recording. For those, say exactly what was and was not verified on a device, and record it
  in `status.md`.
