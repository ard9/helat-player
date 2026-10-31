# Status

Current state of the project. Update at the end of every task.

Last update: 2026-10-31, release `build-81`.

## Features

| Feature | Since | Verified on the phone |
| --- | --- | --- |
| Playback of files and links with libVLC (incl. HEVC 10-bit MKV), aspect modes, tracks | early builds | Yes |
| External SRT/VTT subtitles; subtitle colour and size | early builds | Yes |
| Built-in MKV/WebM subtitles read from the file for saving | | Yes ("much better") |
| Speech detection (VAD) when there are no subtitles | | Partly (earlier versions) |
| Quick save (tap) and editor (hold), Undo | | Yes |
| Clip margins setting (fixed 0.3 s always + margins, default 0.2 s; old values reset) | build-78 | Owner: starts still cut on some lines |
| Fixed pad 0.5 s before, 0.25 s after (+ margins, default 0.2 s) | build-79 | **No** |
| Cards: select several (hold), Select all, Move to box, Delete together; Move on a card; review intervals in Settings; due from the start of a day | build-81 | **No** |
| Cards with picture and meaning; Leitner review | | Yes |
| Blurred review (tap to reveal) | | **No** |
| Backup and restore (ZIP) | | No report |
| Resume where left, Repeat, Speed | | No report |
| Loop button on the video (reworked: instant feedback, waits after jumps, no silent stop) | build-43 | **No** (the first version did not work well online) |
| Clear history; remove the Continue card | build-43 | **No** |
| Version on the home screen | | No report |
| Role-play (record, pause/real-time, playback, compare) | | **No** |
| Role-play scene by time range (From/To) | build-41 | **No** |
| Player controls stay while touched (4 s after letting go) | build-41 | **No** |
| English/Persian, right to left, Settings, guide, welcome card | build-37 | **No** |
| Card audio speed (1×, 0.75×, 0.5×) in card and review screens | build-46 | **No** |
| Card search (text, meaning, film; all boxes) | build-47 | **No** |
| Pronunciation practice in review (record, mine, mine + original) | build-48 | **No** |
| Daily review reminder (Settings, notification, WorkManager) | build-49 | **No** |
| Backup reminder on Home after 14 days | build-50 | **No** |
| Errors explained in the app's language (`describe`) | build-52 | **No** |
| Line choosing moved to tested `LineSearch` (no behaviour change intended) | build-53 | **No** (re-check saving and Repeat) |
| Search subtitles in the player, play from a found line | build-57 | Yes ("fast and good", online) |
| Online films through a local caching proxy (jumps back read from the phone) | build-63 | **No**. build-61 failed on the phone ("VLC could not play"); fixed binding to 127.0.0.1, with automatic fallback to the direct link and a diagnostics message |
| Smaller APK: native libraries compressed (85 → 42 MB) | build-65 | **No** (install and start the app) |
| R8 shrinking of the release APK (no obfuscation) | build-66 | Partly: home, online player, subtitle search, cards, review, role-play setup and guide open fine (owner's screenshots). Not seen: saving without subtitles, role-play recording, practice, reminder |
| Name Helat Player, new icon (rising sun with play), APK `HelatPlayer.apk` | build-67 | **No** |
| About in Settings (version, "Made by ard9", GitHub link); demo GIF and video in the README | build-68 | **No** |
| ‹ Line / Line › step by subtitle line (±5 s without); clips of online films cut through the cache | build-72 | Partly: owner says it works; with subtitles off a press jumped 50 minutes |
| Fix: step by the subtitle track on screen; ±5 s when subtitles are off | build-73 | Owner: back/forward did nothing on online links with built-in subtitles |
| Line stepping: 5 s when the line is more than 10 s away; subtitle-on state also read on time updates | build-74 | **No** |
| Online subtitle reads from the stream cache (Repeat, Loop, saving, back/forward); back/forward waits at most 3 s, spinner, presses not cancelled | build-75 | Yes ("very good") |
| Battery: screen on only while playing; fewer recompositions; online films download in bursts (8 MB low, 48 MB high) | build-77 | **No** (watch playback, seeking, Loop online) |
| Fix: black picture with sound after loading a subtitle file or returning from the background (views re-attached); aspect chosen before the picture applied when it appears; loaded subtitle file in the Subtitles list | build-71 | **No** |
| Lines preloaded in the background; Loop/Repeat/save find lines without a download; loop jumps 150 ms early | build-59 | **No** (owner: start of Loop was slow, pauses between repeats a little) |

"No" means only tests and CI passed; the behaviour on a device is unknown.

## On hold (do not start without the owner asking)

- Make the repository private, possibly with a separate public repository for releases only.
- R8 obfuscation of the release APK (shrinking is on since build-66).

## CI

Actions pinned by SHA on `ubuntu-24.04` since build-45; no deprecation warnings left. Gradle
8.13 gets an "out of date" notice; it stays pinned with AGP 8.11.1 (`build-and-release.md`).

## Proposals waiting for a decision

- If jumps back are still slow online after the caching proxy: try a smaller libVLC
  `network-caching` (risk: stutter on slow connections).

- Leave stream URLs (`sourceUri`) out of card backups, or offer it as a choice.
- Online subtitle search (OpenSubtitles); the owner said "not now".
- Animated splash and the design review findings: text-only player controls, home emphasis on
  today's review, default dialogs, plain empty states, 44 dp buttons on the video, no screen
  transitions. Name and icon are done (Helat Player, `decisions.md`).
- LinkedIn demo video (42 s, 1080x1350) made 2026-10-28 from the owner's screenshots, with the
  series frames, file names and server address replaced by an original drawn scene; the
  player and role-play screens are redrawn from the screenshot and `RolePlayUi.kt`; voices are
  Kokoro TTS, music is synthesized. In `docs/media/` (video, cover, GIF for the README).
- Rename the GitHub repository (owner's action) and check Cafebazaar, Myket and domains for
  "Helat" (owner).
- YouTube links (2026-10-27): the owner said "not now". Options were NewPipe Extractor
  (GPLv3, breaks when YouTube changes, against YouTube's terms) or yt-dlp (~30 MB). Meanwhile
  a downloaded file plus an SRT works with every feature.
- Read subtitle lines ahead while playing, so Loop/Repeat are instant on online films.
  Approved at first, then put back to the owner: over HTTP the reader downloads whole clusters
  (video included), so it roughly doubles the data used while watching.

## Known limitations

See `README.md` (Limitations) and `docs/saving-lines.md`.
