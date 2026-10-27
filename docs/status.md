# Status

Current state of the project. Update at the end of every task.

Last update: 2026-10-27, after the controls and role-play range change (see the latest release).

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
| Loop button on the video | | **No** |
| Version on the home screen | | No report |
| Role-play (record, pause/real-time, playback, compare) | | **No** |
| Role-play scene by time range (From/To) | after build-38 | **No** |
| Player controls stay while touched (4 s after letting go) | after build-38 | **No** |
| English/Persian, right to left, Settings, guide, welcome card | build-37 | **No** |

"No" means only tests and CI passed; the behaviour on a device is unknown.

## On hold (do not start without the owner asking)

- Make the repository private, possibly with a separate public repository for releases only.
- R8 minification and obfuscation of the release APK.

## Proposals waiting for a decision

- Pin GitHub Actions by commit SHA instead of `@v4` (see `docs/security.md`).
- Leave stream URLs (`sourceUri`) out of card backups, or offer it as a choice.
- Move logic out of `ui/PlayerScreen.kt` (~1270 lines) into a testable class without changing
  behaviour (see `docs/architecture.md`).
- Online subtitle search (OpenSubtitles); the owner said "not now".

## CI warnings to handle before they break the build

- Node.js 20 actions are deprecated; runners force Node 24. Update `actions/checkout`,
  `actions/setup-java` (v5 exists), `actions/upload-artifact`, `gradle/actions/setup-gradle`.
- `ubuntu-latest` moves to Ubuntu 26 from 2026-10-19; watch the next builds.

## Known gaps

- Error details that come from exceptions stay in English inside translated messages, e.g.
  "Could not restore: This backup is from a newer version" (`CardBackup`), I/O and libVLC
  errors. Not yet proposed as a change.

## Known limitations

See `README.md` (Limitations) and `docs/saving-lines.md`.
