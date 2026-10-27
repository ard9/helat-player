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

## Controls

Tap the video to show or hide them; they hide after 4 s while playing.

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
- **Loop**: repeats that range until tapped again; seeking far away ends it.
- **Speed**: 1×, 0.75×, 0.5×, 1.25× (`RATES`).

## Resume

`RecentRepository` stores the position per URI when leaving or pausing in the background;
`ResumePolicy` decides which positions are worth resuming (not the very start or the end).
The player jumps there once playback starts and offers "Start over".

## Not covered by tests

Everything in this file runs on libVLC and Compose; verify on a device (see `status.md`).
