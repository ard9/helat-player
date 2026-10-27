# Role-play

The user and the actor take turns saying the lines of a scene. On the user's lines the film
goes quiet and the user's voice is recorded; afterwards the scene plays back with the user's
voice in place of the actor's.

Code: `roleplay/RolePlayLogic.kt` (pure, tested), `roleplay/VoiceRecorder.kt`,
`roleplay/RolePlaySession.kt`, `ui/RolePlayUi.kt`, hookup in `ui/PlayerScreen.kt`
(`openRolePlay`, `startRolePlay`, `closeRolePlay`, `sceneLinesFrom`, `sceneLinesBetween`).

## Setup (`RolePlaySetupDialog`)

- Scene, two modes (the last one used is remembered):
  - **Number of lines**: the next N subtitle lines from the paused moment (default 10, 2–200):
    `SubtitleLookup.scene` or `MkvSubtitles.linesFrom`.
  - **Time range**: From/To typed as m:ss (ASCII), each with "Now"; opens as now to now + 1 min.
    `SceneRange.check` refuses invalid times, To not after From, and ranges over 10 min (online
    files are read over the network). Lines that *start* in the range, at most 200:
    `SubtitleLookup.between` or `MkvSubtitles.linesBetween`.
  - Lines come from the subtitle file if one is loaded, else the best built-in text track.
- Roles alternate (`Roles.alternate`), actor first unless "I start"; tapping a line flips it.
- Options, remembered in `SettingsRepository`: mode, line count, "Pause the film on my lines"
  (default on), "Show my lines" (default on).
- Needs `RECORD_AUDIO`, asked only when Start is tapped.

## Session (`RolePlaySession`)

Phases: Acting → Finished; PlayingBack; Comparing. It drives the player only through the
`ScenePlayer` interface (time, play, pause, seek, mute).

- **Acting**: plays from 1.5 s before the first line. At each user line (120 ms early) the
  film is muted; with *pause* it also pauses and recording stops on a pause after speech
  (`SpeechEndDetector`: 900 ms silence, gives up after 6 s without speech, max 20 s), then
  the film jumps past the actor's line. In *real time* the recording stops at the end of the
  actor's line. Done ends a turn, Skip leaves the line to the actor.
- Recordings: 16 kHz mono WAV (`WavWriter`) in `cacheDir/roleplay/<time>/`, with the moment
  speech began (`speechStartMs`), so playback skips the silence before the first word.
- **Play my scene**: no pauses; on user lines the film is muted and the recording plays over
  the picture; the film stays quiet until the actor's line ends.
- **Compare**: plays the actor's line or the user's recording for one line.
- Seeks wait until the player reports the new position (`seekAndPlay`), because libVLC may
  still report the old one for a moment.
- Closing the session or leaving the player deletes the recordings.

## Not verified on a device

Recording, timing and playback sync have not been confirmed on the phone (see `status.md`).
Headphones are recommended so the film is not recorded with the voice.
