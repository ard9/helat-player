# Cards, review, backup

Code: `data/Card.kt`, `data/CardRepository.kt`, `data/CardBackup.kt`, `ui/BoxScreen.kt`,
`ui/CardScreen.kt`, `ui/ReviewScreen.kt`, `ui/HomeScreen.kt`.

## Card

`Card`: id, text, meaning, source title and URI, start/end ms, audio file name, image file
name (optional), box (1–5), created and next review time. Stored in `files/cards.json`;
audio in `files/clips/`, picture in `files/images/`. Fields added later must be optional when
reading so older cards load (`CardTest` covers loading old JSON).

## Leitner

Five boxes. "I knew it" moves a card up one box, "Didn't know" back to Box 1. Intervals in
days per box, set in Settings (`data/BoxIntervals`, default 1, 2, 4, 8, 16; 1–365; a box is
never shorter than the one before it, `with` raises later and lowers earlier boxes; stored as
`box_interval_1..5`). A card is due from the start of the day (phone's time zone,
`Leitner.dueAt`), so the hour of review does not push it later. New intervals apply from each
card's next answer; `nextReviewAt` already set is kept. Moving cards by hand
(`Leitner.moveTo`) schedules them as if they had just reached that box; Box 1 is due at once.
Intervals are settings, not part of card backups.

## Screens

- Home: due count, box bars, Start review, Browse cards, Back up / Restore.
- `BoxScreen`: the five boxes as tabs (`BoxBars`), cards of the selected box. A search field
  finds cards in all boxes (`CardSearch`: every word must appear in text, meaning or film;
  case, Arabic ي/ك and the half space are ignored; tested).
- `BoxScreen` selection: a long press selects a card; then taps add or remove cards, Select
  all takes every card shown (the box, or the search results), and Move to box
  (`MoveToBoxDialog` in `ui/CardActions.kt`) or Delete (with a confirmation;
  `CardRepository.updateAll` / `deleteAll`, audio and pictures too) acts on all of them. Back
  or ✕ ends the selection.
- `CardScreen`: picture with text, play, edit text/meaning, move to another box, delete (with its files).
- Card audio (`ClipAudioPlayer`, Media3) has a speed button in the card and review screens:
  1×, 0.75×, 0.5× (pitch kept), stored as `clip_speed` in `SettingsRepository`.
- `ReviewScreen`: due cards one by one. The audio plays first; picture, text and meaning are
  blurred (`Hidden`, `Modifier.blur` on Android 12+, a solid cover on older versions) until
  tapped or Show all. Answer buttons appear once the text is shown. Without audio the text is
  shown at once.
- Pronunciation practice in review (`ui/PracticeRow.kt`): "Say it" records with
  `VoiceRecorder` (stops on a pause after speech, max 3× the clip or 8 s), then "Mine" plays
  the voice from just before speech began and "Mine + original" plays it followed by the
  card audio. One file, `cacheDir/practice/voice.wav`, deleted when the card changes or the
  screen closes. Microphone permission is asked on first use. Not verified on a device.

## Daily review reminder (`reminder/`)

- Off by default; Settings turns it on (asking for `POST_NOTIFICATIONS` on Android 13+) and
  sets the time (20:00 at first). Stored as `reminder_on` / `reminder_minutes`.
- `ReviewReminder.schedule` enqueues one WorkManager job for the next time
  (`ReminderTime.delayUntilNext`, pure and tested). `ReminderWorker` posts a notification if
  cards are due, in the app's language (`inAppLanguage`), then schedules the next day.
  WorkManager keeps the job across phone restarts. Not verified on a device.

## Backup and restore (`CardBackup`)

- ZIP with `cards.json` first (`{"format":1,"cards":[...]}`), then `clips/` and `images/`.
  Bump `CardBackup.FORMAT` only for incompatible changes.
- Restore treats the file as untrusted: entry names must stay inside `clips/` and `images/`
  (no zip-slip), existing files are never overwritten, sizes are limited, damaged or newer
  data is refused, cards already present (same id) or incomplete are skipped.
- The backup includes `sourceUri` (may contain stream tokens); the app tells the user to keep
  it private. See `security.md`.
- `CardBackupTest` covers round trips and hostile backups.
- Backup reminder: Home shows a card when cards have not been backed up for 14 days (counted
  from the oldest card if never), until a backup succeeds; ✕ hides it for 7 days
  (`BackupReminder`, pure and tested; `last_backup_at`, `backup_snoozed_until` in settings).
