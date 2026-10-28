# Status

Current state of the project. Update at the end of every task.

Last update: 2026-10-28, release `build-61`.

## Features

| Feature | Since | Verified on the phone |
| --- | --- | --- |
| Playback of files and links with libVLC (incl. HEVC 10-bit MKV), aspect modes, tracks | early builds | Yes |
| External SRT/VTT subtitles; subtitle colour and size | early builds | Yes |
| Built-in MKV/WebM subtitles read from the file for saving | | Yes ("much better") |
| Speech detection (VAD) when there are no subtitles | | Partly (earlier versions) |
| Quick save (tap) and editor (hold), Undo | | Yes |
| Clip margins setting | | No report |
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
| Online films through a local caching proxy (jumps back read from the phone) | build-61 | **No** (also check normal playback and seeking online) |
| Lines preloaded in the background; Loop/Repeat/save find lines without a download; loop jumps 150 ms early | build-59 | **No** (owner: start of Loop was slow, pauses between repeats a little) |

"No" means only tests and CI passed; the behaviour on a device is unknown.

## On hold (do not start without the owner asking)

- Make the repository private, possibly with a separate public repository for releases only.
- R8 minification and obfuscation of the release APK.

## CI

Actions pinned by SHA on `ubuntu-24.04` since build-45; no deprecation warnings left. Gradle
8.13 gets an "out of date" notice; it stays pinned with AGP 8.11.1 (`build-and-release.md`).

## Proposals waiting for a decision

- If jumps back are still slow online after the caching proxy: try a smaller libVLC
  `network-caching` (risk: stutter on slow connections).

- Leave stream URLs (`sourceUri`) out of card backups, or offer it as a choice.
- Online subtitle search (OpenSubtitles); the owner said "not now".
- New name, logo and animated splash (paused 2026-10-27 until the owner has tested build-54).
  The owner rejected three icon concepts and wants a name first: a Kurdish (Kurmanji) word in
  Latin letters, logo drawn from the name. Shortlist offered: Hevok (sentence, preferred),
  Bibêje (say it!), Ziman, Deng, Dengbêj, Dîmen (meaning not yet confirmed). "Cuecard" is taken.
  Keep `applicationId` unchanged when renaming, or the update will not install over the old app.
  Design review findings to address then: text-only player controls, home emphasis on today's
  review, default dialogs, plain empty states, 44 dp buttons on the video, no screen transitions.
- YouTube links (2026-10-27): the owner said "not now". Options were NewPipe Extractor
  (GPLv3, breaks when YouTube changes, against YouTube's terms) or yt-dlp (~30 MB). Meanwhile
  a downloaded file plus an SRT works with every feature.
- Read subtitle lines ahead while playing, so Loop/Repeat are instant on online films.
  Approved at first, then put back to the owner: over HTTP the reader downloads whole clusters
  (video included), so it roughly doubles the data used while watching.

## Known limitations

See `README.md` (Limitations) and `docs/saving-lines.md`.
