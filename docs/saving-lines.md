# Saving a line

Code: `ui/PlayerScreen.kt` (`findLine`, `quickSave`, `openEditor`), `ui/CardEditorDialog.kt`,
`subtitle/`, `speech/`, `clip/`.

## Entry points

- **Tap** the bookmark button → `quickSave()`: runs in the background, the film keeps
  playing, a short notice says Saved with Undo. A second tap on a line already saved this
  session (over 50 % overlap) says "Already saved".
- **Hold** → `openEditor()`: pauses, grabs the frame, opens `CardEditorDialog` and fills it
  as the line is found. The user can edit start/end (m:ss.s, ASCII), Preview, text, meaning,
  and switch "Text from" between built-in text tracks.

## Finding the line: `findLine(now)`, in order

1. **External subtitle file** loaded by the user (`SubtitleLookup.nearMoment`) → exact times
   and text.
2. **Built-in MKV/WebM text track** → `MkvSubtitles.linesAround(now)` reads only ~12 s of the
   file before the moment (HTTP Range for links). `player/LineSearch` (pure, tested) picks the
   line. Track order (`LineSearch.preferredOrder`): the track
   shown in VLC if it can be matched and is not Persian, then the default non-Persian, then
   any non-Persian, then the rest. A Persian track fills the meaning
   (`SubtitleLookup.overlapping`).
3. **Speech detection** (`SpeechFinder`): cuts 8 s before / 6 s after the moment, runs
   Silero VAD (`SileroVad`, ONNX), and `SentenceFinder` picks the sentence around the moment
   (hysteresis, short-gap merge, long-speech split). It reports when the sentence may extend
   past the checked part. The card then has no text.

The found range is widened by the user's **clip margins** (default 0.45 s each side, max
1.5 s, `SettingsRepository`), then `ClipService` cuts the audio and the card goes to Box 1.

## Reading MKV/WebM (`subtitle/mkv/`)

- `ByteSource`: local file descriptor or HTTP Range requests, with a total read budget (64 MB).
- `EbmlReader`: streaming EBML with element and block size caps; `ReadLimitException`.
- `MkvSubtitles`: track headers (codec, language, name, default, compression), SeekHead and
  Cues to jump near the moment, clusters before it (a line may start in an earlier cluster),
  `linesFrom` for role-play scenes. Damaged blocks are skipped.
- Text tracks: SRT (`S_TEXT/UTF8`), ASS/SSA, WebVTT. `SubtitleTextCleaner` removes ASS
  fields, tags and drawings, SRT tags and entities.
- Track labels: `MkvSubtitleTrack.label(display, unnamed)` names the language in the app's
  language and keeps the track's own name, e.g. "فارسی (Persian)".

## Reading all lines (subtitle search)

`MkvSubtitles.allLines` returns every line of every text track. If the Cues index has an
entry with `CueRelativePosition` for each text track (mkvmerge and ffmpeg write them), it
reads only those blocks: one small range request for each cluster header, then one for each
group of nearby blocks (`blockWindow`, 16 KB), several at once online. Otherwise a local file
is scanned from the first cluster to the end (progress from the Info duration), and an online
file throws `NoSubtitleIndexException` rather than downloading the film.

## Cutting the audio (`clip/`)

`ClipService` tries Media3 `TransformerClipper` (m4a) and falls back to `VlcClipper`
(libVLC sout → wav). libVLC may ignore `:start-time`, so `VlcClipper` also stops on
`TimeChanged` past the end and `WavTrim.keepLast` keeps only the requested duration.
`WavReader` is tolerant (any channels/rate, unset sizes) and resamples for VAD.

## Limitations

- MP4 built-in subtitles are not read; picture subtitles (PGS/VobSub) cannot be read as text.
  Both fall back to speech detection.
- Speech detection cannot separate sentences spoken without a pause.
