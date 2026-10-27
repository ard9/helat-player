# Lingua Player

An Android video player for language learning.

- Plays local files and direct links or streams in every format VLC supports (powered by libVLC).
- Aspect ratio modes (best fit, fit screen, fill, 16:9, 4:3, original), full screen, audio and subtitle track selection.
- Shows subtitles from an external file (SRT or VTT) or from a subtitle track inside the video,
  in the colour and size you choose. The player follows the phone's rotation; landscape is full screen.
- **Save**: saves the sentence being spoken as a card with its audio clip and text. Times and
  text come from a subtitle file, else from the video's own subtitle track, else from the pauses
  around the sentence. Everything can be checked with Preview and adjusted before saving.
- **Cards**: each saved line keeps its audio, a frame of the video, the text and your meaning.
  Tap a card to open it; review them as flashcards in a 5-box Leitner system.
- **Listening practice**: Repeat plays the current line again, the small loop button above the
  save button repeats it until tapped again, and Speed slows playback down (0.5×–1.25×).
- **Role-play**: from the player menu, take turns with the actor over the next lines (as many
  as you like) or over a time range you choose (From/To, up to 10 minutes). On your lines the film goes quiet and your voice is recorded; the film either
  pauses until you finish or carries on in real time. Then play the scene back without pauses,
  your voice over the picture in place of the actor's (silence before your first word is
  skipped), or compare each of your lines with the actor's. Needs subtitles and the microphone;
  headphones help. Recordings stay on the phone and are deleted when the session is closed.
- The home screen shows the app version; its last number is the GitHub build number.
- Videos continue where you left them.
- **English and Persian**: the app follows the phone's language, or choose one under Settings
  (gear icon on the home screen). Persian uses a right-to-left layout and the Vazirmatn font.
- **Guide**: the "?" icon on the home screen opens a step-by-step guide to every feature.
- **Backup**: Back up saves all cards in one ZIP file; Restore adds the cards from such a file.
  Cards are otherwise kept only on the phone.

Built with Kotlin, Jetpack Compose, libVLC (playback) and Media3 (audio clipping).

## Install

Every push to `main` builds an APK with GitHub Actions and publishes it on the
[Releases](../../releases) page. Download `LinguaPlayer.apk` on your phone and open it
(you may need to allow "Install unknown apps" for your browser or file manager).

## How to use

1. Open a video file, or paste a link and tap **Play link**.
2. Tap the video to show controls. **Load subtitle file** picks an `.srt` or `.vtt` file;
   **Subtitles** chooses a track inside the video.
3. When you hear a sentence you want to learn, tap the small bookmark button on the right.
   The line is saved in the background while the film keeps playing (Undo is offered for a
   moment). Hold the button instead to pause and check the times, text and meaning first.
4. From the home screen, tap **Review**. The picture, text and meaning start blurred: listen
   first, tap each part to reveal it (or Show all), then answer:
   - **I knew it** moves the card up one box.
   - **Didn't know** moves it back to Box 1.

Review intervals: Box 1 every day, Box 2 every 2 days, Box 3 every 4, Box 4 every 8, Box 5 every 16.

## Limitations

- The APK is built for 64-bit ARM phones (arm64-v8a) only.
- Subtitle tracks inside MKV/WebM files are read straight from the file (only the part
  around the saved moment, also for online links that allow partial downloads). Text tracks
  (SRT, ASS, WebVTT) give exact times and text; when there is a Persian track, it fills the
  meaning. Picture subtitles (PGS, VobSub) cannot be read as text.
- Without any subtitles, Save looks at the audio from 8 s before to 6 s after the moment you
  tap it and finds the pauses around the sentence with Silero VAD (on the device). Speech
  without pauses, or very long sentences, may need the times adjusted by hand.

## Fonts

Archivo, Atkinson Hyperlegible and Vazirmatn are used under the SIL Open Font License, and the Silero VAD
model under the MIT License (see `licenses/`).

## Signing (for maintainers)

The release APK is signed with a key stored in GitHub Secrets, so every new build installs
over the previous one. Add these repository secrets under
**Settings → Secrets and variables → Actions**:

| Secret | Value |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | The keystore file, base64-encoded |
| `SIGNING_STORE_PASSWORD` | Keystore password |
| `SIGNING_KEY_ALIAS` | Key alias |
| `SIGNING_KEY_PASSWORD` | Key password |

Without these secrets the workflow builds a debug APK instead. Never commit the keystore.

## Build locally

Requires JDK 17 and the Android SDK. Open the project in Android Studio, or run
`gradle assembleDebug`.
