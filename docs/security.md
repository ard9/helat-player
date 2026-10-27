# Security

Flag security issues in any change: what, where, impact, severity, proposed fix (see the
working agreement in `AGENTS.md`).

## Permissions and data

- Permissions: `INTERNET`; `RECORD_AUDIO`, asked at runtime only when role-play or
  pronunciation practice starts. Recordings are written to `cacheDir/roleplay/…` and
  `cacheDir/practice/` and deleted when the session or card is closed.
- No analytics, no own server. Network use: playing links and HTTP Range reads of the same
  links for subtitles.
- All data is app-private and excluded from Android backup (`allowBackup=false`, data
  extraction rules), because stream URLs (which may contain tokens) are stored in recents and
  cards.
- The user's own card backup includes `sourceUri`; the app tells them to keep it private.
- `usesCleartextTraffic=true` so http streams play; the app warns before opening any http link.
- "Open with" accepts only `content`, `http` and `https` URIs.
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
| Release APK is not minified or obfuscated (R8). | Low | On hold |
