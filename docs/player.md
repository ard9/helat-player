# Player

Code: `ui/PlayerScreen.kt`, `player/VlcMedia.kt`, `player/FrameGrabber.kt`, `LinguaApp.kt`.

## Playback

- **libVLC**, not Media3 (see `decisions.md`). `VlcMedia` opens `content://` sources through a
  file descriptor and http(s) links directly.
- One libVLC instance per subtitle style, created in `LinguaApp.acquireLibVlc` with
  `--freetype-color` and `--sub-text-scale`, reference counted. A style change applies to
  built-in subtitle tracks the next time a video is opened.
- **`StartGate`**: playback starts only once the media is set *and* the `VLCVideoLayout` is
  attached to the window; otherwise the video was sometimes black while audio played.
- Errors: `EncounteredError` shows a sticky message; opening errors too.
- **Online films go through a caching proxy** (`stream/`): `openVlcMedia` starts a
  `CachingProxy` (HTTP server on 127.0.0.1, random secret path) and gives libVLC its address.
  The proxy fetches the film with `HttpUpstream` (Range requests, redirects) and keeps every
  256 KB chunk in `ChunkStore` (`cacheDir/stream-cache/`, 200 MB for all films, least recently
  used removed first). Jumps back (Loop, Repeat, −10s, seek bar) are then read from the phone.
  Why: libVLC's own prefetch buffer prefers reading ahead and drops already played data, so
  jumping back was sometimes instant and sometimes a new download (`decisions.md`). If the
  proxy cannot start, the link is played directly; if libVLC reports an error while playing
  through it, the player reopens the link directly (once) and shows the proxy's diagnostics
  (`CachingProxy.diagnostics`: request count and last problem) so the cause can be found.
- The proxy binds to `127.0.0.1` explicitly: on Android `InetAddress.getLoopbackAddress()`
  can be `::1`, and build-61 failed on the phone with "VLC could not play this media" (most
  likely this). Verified locally: desktop VLC 3.0.20 plays and seeks through the proxy, and
  `HttpUpstream` ranges match direct downloads over HTTPS with redirects.

## Controls

Tap the video to show or hide them. While playing they hide 4 s after the last touch; they
stay while a finger is on the screen (an `Initial`-pass pointer observer on the root `Box` sees
every touch, also scrolling the control row, without taking it) and while a dialog opened from
the player is showing.

- Top bar: back, title. Centre: Repeat, −10s, play/pause, +10s. Bottom: seek bar and a
  scrolling row: Aspect, Audio, Subtitles, Load subtitle file, Subtitle style, Speed,
  Role-play, Clip margins, Full screen/Portrait, Auto-rotate.
- The seek bar and the centre row stay left to right in Persian.
- On the right side of the video (left in Persian): the faint **loop** button above the faint
  **save** button (`LoopButton`, `QuickSaveButton`). They stay visible while watching.
- **Edge-to-edge** (targetSdk 35): controls use `WindowInsets.safeDrawing` padding so the
  system bars and camera cutout do not cover them.

## Rotation and full screen

The player follows the sensor (`FULL_SENSOR`); landscape hides the system bars. Full screen
locks landscape, Portrait locks portrait, Auto-rotate unlocks. Leaving the player restores
the orientation and system bars. The activity handles orientation changes itself
(`configChanges` in the manifest), so the player is not recreated on rotation.

## Repeat, loop, speed

- **Repeat**: jumps to the start of the current line (`lineRangeAt`: subtitle file, else the
  best built-in text track), or 5 s back without subtitles.
- **Loop**: the button lights up at once (with a spinner while the line is found, which reads
  the file and can take seconds on online links), then repeats that range. `player/LineLoop`
  (pure, tested) decides when to jump back: after each jump it waits until libVLC reports a
  time near the start again (or 4 s pass), because libVLC may still report the old position.
  It jumps back 150 ms before the end of its range (`LOOP_EARLY_MS`; the range already has the
  after-line margin) to shorten the pause between repeats, never before the middle of the line.
  The loop never ends by itself; it ends on the button, a user seek (seek bar, ±10s, Repeat),
  opening the editor or role-play, or the end of the film, each with "Loop off" (role-play
  silently).
- **Speed**: 1×, 0.75×, 0.5×, 1.25× (`RATES`).

## Subtitle search

"Search subtitles" (player menu) pauses the film and opens `SubtitleSearchDialog`
(`ui/SubtitleSearchUi.kt`). Every text track is searched (or the loaded subtitle file instead);
`SubtitleSearch` (pure, tested) uses the same loose matching as card search (`text/TextMatch`:
case, ي/ك, half space; every word must appear) and `TextMatch.highlights` marks the words. A
result plays the film from 0.3 s before the line; closing resumes playback if it was playing.

The film's lines come from `MkvSubtitles.allLines` (`saving-lines.md`), read once per film with
progress, and kept in `cacheDir/subtitle-index/` (`SubtitleIndexCache`,
file named by a SHA-256 of the address, last 20 films). An online file without subtitle cues
shows "load a subtitle file" instead of downloading the film.

**Preloading:** 3 s after playback starts, the lines are read in the background when that is
cheap (from the cache, or through the subtitle cues: `canReadAllLinesCheaply`); a local file
without cues is only scanned when the user searches. Once read (`filmLines`), Loop, Repeat,
saving and role-play look lines up in memory instead of downloading ~12 s of the film. Before
that, or without cues, they read around the moment as before.

## History

Home lists recent videos (`RecentRepository`, max 10). ✕ removes one, Clear history (with a
confirmation) removes all items and resume positions. Removing also releases the lasting read
access to local files that was taken to reopen them. Cards are not affected.

## Resume

`RecentRepository` stores the position per URI when leaving or pausing in the background;
`ResumePolicy` decides which positions are worth resuming (not the very start or the end).
The player jumps there once playback starts and offers "Start over".

## Not covered by tests

Everything in this file runs on libVLC and Compose; verify on a device (see `status.md`).
