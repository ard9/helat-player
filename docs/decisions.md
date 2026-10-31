# Decisions

Why things are the way they are. Do not undo these without a new reason; add new decisions at
the end with the date.

## 2026-10-26 Playback is libVLC, not Media3

Media3 failed with `DECODING_FAILED` on HEVC 10-bit (`hvc1.2.4.L120.90`) on the owner's
phone; libVLC plays it with software decoding. Media3 is kept only for clipping and for
playing saved clips. Cost: larger APK, toolchain pins (`build-and-release.md`).

## 2026-10-27 StartGate before playback

Starting before the video view was attached sometimes gave a black picture with sound.
Playback now waits for both the media and the attached view.

## 2026-10-27 Built-in subtitles are read from the file, not the screen

An earlier version watched libVLC's subtitle surface and used OCR (ML Kit). It failed: with
software decoding libVLC blends subtitles into the video, so there is no separate layer.
Removed, together with ML Kit. MKV/WebM tracks are parsed directly instead, also over HTTP
Range, which gives exact times and text.

## 2026-10-27 Speech detection only as a fallback, in a short window

Without subtitles, Silero VAD looks 8 s before and 6 s after the moment (a 15 s window was
too much). It cannot split sentences spoken without a pause, so subtitles always come first.

## 2026-10-27 Work around libVLC ignoring `:start-time`

On some files libVLC writes audio from 0 to the end time. `VlcClipper` stops on
`TimeChanged` past the end and `WavTrim` keeps only the last requested duration.

## 2026-10-27 Quick save by tap, editor by hold

The owner wanted saving to be quick and not disturb watching: a small faint button saves in
the background with Undo; holding opens the editor for checking.

## 2026-10-27 Edge-to-edge insets

With targetSdk 35 the system navigation bar covered the controls in landscape; controls use
`WindowInsets.safeDrawing` padding.

## 2026-10-27 Role-play playback without pauses

The owner wanted the final playback natural: the user's voice over the picture in place of
the actor's, skipping the silence before the first word, with no pauses.

## 2026-10-27 Language by overriding only the locale

`attachBaseContext` overrides only the locale (not the whole configuration) because the
activity handles rotation itself; a full override would freeze orientation. Film times stay
ASCII because they are typed in the editor.

## 2026-10-28 Online films through a local caching proxy

Loop repeats online were sometimes instant, sometimes slow. libVLC 3's `prefetch` stream filter
keeps played data only while its buffer has room and drops it to read further ahead, so a
jump back to the line start was a new HTTP request whenever the buffer had filled. A bigger
buffer only delays that. The app now plays online films through `CachingProxy`, which keeps
downloaded chunks on disk (200 MB), so jumps back are local.

## 2026-10-31 Cards due from the start of a day; intervals chosen by the user

Due times were "answer time + N × 24 h", so a card answered at 22:00 was not due the next
morning. Now a card is due from 00:00 (phone's time zone) N days later. The owner wanted the
intervals per box to be his choice; a box is kept no shorter than the one before it so
knowing a card never brings it back sooner.

## 2026-10-30 A fixed pad around subtitle lines

The owner found that clips came out right with the margins but cut words when the margins were
made smaller. Subtitle times are for reading, not speech. Owner's choice: always keep 0.5 s
before and 0.25 s after a subtitle line (first 0.3 s each side in build-78; starts were still
cut sometimes), with the user's margins on top (default 0.2 s). Snapping
the clip to the speech with speech detection was proposed and not chosen for now.

## 2026-10-29 Battery: screen on only while playing, read-ahead in bursts

The screen used to stay on as long as the player was open, also paused. The cellular radio
uses much power and stays awake while data trickles; downloading 40 MB at once and then
closing the connection lets it sleep between bursts, at the cost of up to 48 MB downloaded
and possibly not watched.

## 2026-10-28 Name: Helat Player

"Lingua Player" was already the name of another Android app for learning languages from films
(a VLC fork on Google Play, Cafebazaar and Myket), with the same audience. The owner wants one
Kurdish brand for all products ("Helat AI", "Helat Player", ...): Helat (Sorani "hełat", the sun
rising). Checked: no software product named Helat (one inactive events app in Indonesia);
Berbang (sunset in Sorani), Hevi, Kani, Zana, Pasho and Roj were rejected for meaning or
existing products. The icon is the rising sun with a play button cut into it (one colour, so it
also works as the themed icon). `applicationId` and the Kotlin package stay
`io.github.ard9.linguaplayer`, so updates install over the old app and cards are kept.

## 2026-10-28 Compressed native libraries; ONNX Runtime kept

The owner downloads every build on the phone, so download size matters more than install
size. Compressing the native libraries halves the APK (85 → ~42 MB) at the cost of ~30 MB
more on the phone. Removing ONNX Runtime (~7 MB compressed) was considered and rejected: the
Silero model also detects the end of the user's speech in role-play and pronunciation
practice, and a loudness detector is much worse on film audio.

## 2026-10-27 Docs split by area

`AGENTS.md` is a short entry point; details live in `docs/`, one file per area, each under
200 lines, so no single file grows too long to read. `DocsTest` enforces it.
