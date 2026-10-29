# Security

Flag security issues in any change: what, where, impact, severity, proposed fix (see the
working agreement in `AGENTS.md`).

## Permissions and data

- Permissions: `INTERNET`; `RECORD_AUDIO`, asked at runtime only when role-play or
  pronunciation practice starts. Recordings are written to `cacheDir/roleplay/…` and
  `cacheDir/practice/` and deleted when the session or card is closed.
  `POST_NOTIFICATIONS` (Android 13+), asked only when the daily reminder is turned on.
- No analytics, no own server. Network use: playing links and HTTP Range reads of the same
  links for subtitles.
- All data is app-private and excluded from Android backup (`allowBackup=false`, data
  extraction rules), because stream URLs (which may contain tokens) are stored in recents and
  cards.
- The user's own card backup includes `sourceUri`; the app tells them to keep it private.
- `usesCleartextTraffic=true` so http streams play; the app warns before opening any http link.
- "Open with" accepts only `content`, `http` and `https` URIs.
- Subtitle search keeps a film's subtitle lines in `cacheDir/subtitle-index/`, named by a
  SHA-256 of the address so stream tokens are not written; the last 20 films; cache only.
- Online films are played (and their clips cut) through a local proxy (`stream/CachingProxy`): it listens only on
  127.0.0.1, answers only a path with a 128-bit random secret, GET/HEAD only, headers limited
  to 8 KB, at most 8 connections. Another app on the phone would need the secret to read the
  stream. It fetches the film with the platform's HTTPS (Android certificate checks) instead
  of libVLC's own TLS. Downloaded chunks are kept in `cacheDir/stream-cache/` (folders named by
  a SHA-256 of the address; 200 MB in total), app-private and cleared by the system if needed.
- Lasting read access to local files is taken only to reopen them from the recent list and is
  released when they are removed from it or the history is cleared.

## Untrusted input

Media files, network data and backup files. Size limits exist everywhere they are read:
subtitle file 5 MB, EBML element and block caps, HTTP read budget 64 MB, WAV reading limits,
backup entry sizes and paths (`cards-and-review.md`). Keep such limits when adding parsers.
XML parsed in tests disallows DTDs.

libVLC and ONNX Runtime parse untrusted data in native code; keep them updated.

## Build and secrets

- Signing secrets live only in GitHub Secrets; the keystore file is removed after each run
  and never committed (`build-and-release.md`).
- The workflow has `contents: write` to publish releases.
- Actions are pinned by commit SHA, so a moved or hijacked tag cannot change the code that
  runs next to the signing secrets.

## Open items

| Item | Severity | Status |
| --- | --- | --- |
| Backups contain stream URLs with possible tokens. | Low | Proposal: leave them out or make it a choice |
| The repository is public; the owner wants it private / releases only. | — | On hold |
| Release APK is shrunk by R8 but not obfuscated (names readable). | Low | Obfuscation on hold |
