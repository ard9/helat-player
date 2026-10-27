# Cards, review, backup

Code: `data/Card.kt`, `data/CardRepository.kt`, `data/CardBackup.kt`, `ui/BoxScreen.kt`,
`ui/CardScreen.kt`, `ui/ReviewScreen.kt`, `ui/HomeScreen.kt`.

## Card

`Card`: id, text, meaning, source title and URI, start/end ms, audio file name, image file
name (optional), box (1–5), created and next review time. Stored in `files/cards.json`;
audio in `files/clips/`, picture in `files/images/`. Fields added later must be optional when
reading so older cards load (`CardTest` covers loading old JSON).

## Leitner

Five boxes. "I knew it" moves a card up one box, "Didn't know" back to Box 1. Intervals:
Box 1 every day, then 2, 4, 8, 16 days (`Leitner`). A card is due when `nextReviewAt` has
passed.

## Screens

- Home: due count, box bars, Start review, Browse cards, Back up / Restore.
- `BoxScreen`: the five boxes as tabs (`BoxBars`), cards of the selected box. A search field
  finds cards in all boxes (`CardSearch`: every word must appear in text, meaning or film;
  case, Arabic ي/ك and the half space are ignored; tested).
- `CardScreen`: picture with text, play, edit text/meaning, delete (with its files).
- Card audio (`ClipAudioPlayer`, Media3) has a speed button in the card and review screens:
  1×, 0.75×, 0.5× (pitch kept), stored as `clip_speed` in `SettingsRepository`.
- `ReviewScreen`: due cards one by one. The audio plays first; picture, text and meaning are
  blurred (`Hidden`, `Modifier.blur` on Android 12+, a solid cover on older versions) until
  tapped or Show all. Answer buttons appear once the text is shown. Without audio the text is
  shown at once.

## Backup and restore (`CardBackup`)

- ZIP with `cards.json` first (`{"format":1,"cards":[...]}`), then `clips/` and `images/`.
  Bump `CardBackup.FORMAT` only for incompatible changes.
- Restore treats the file as untrusted: entry names must stay inside `clips/` and `images/`
  (no zip-slip), existing files are never overwritten, sizes are limited, damaged or newer
  data is refused, cards already present (same id) or incomplete are skipped.
- The backup includes `sourceUri` (may contain stream tokens); the app tells the user to keep
  it private. See `security.md`.
- `CardBackupTest` covers round trips and hostile backups.
