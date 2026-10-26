# Lingua Player

An Android video player for language learning.

- Plays local files and direct links or streams in every format VLC supports (powered by libVLC).
- Aspect ratio modes (best fit, fit screen, fill, 16:9, 4:3, original), full screen, audio and subtitle track selection.
- Shows subtitles from an external file (SRT or VTT) or from a subtitle track inside the video,
  in the colour and size you choose. The player follows the phone's rotation; landscape is full screen.
- **Save**: with a subtitle file, saves the sentence being spoken as a card with its audio clip and text.
  Without one, pick a start and end time by hand (with preview and an optional note).
- **Cards**: each saved line keeps its audio, a frame of the video, the text and your meaning.
  Tap a card to open it; review them as flashcards in a 5-box Leitner system.

Built with Kotlin, Jetpack Compose, libVLC (playback) and Media3 (audio clipping).

## Install

Every push to `main` builds an APK with GitHub Actions and publishes it on the
[Releases](../../releases) page. Download `LinguaPlayer.apk` on your phone and open it
(you may need to allow "Install unknown apps" for your browser or file manager).

## How to use

1. Open a video file, or paste a link and tap **Play link**.
2. Tap the video to show controls. **Load subtitle file** picks an `.srt` or `.vtt` file;
   **Subtitles** chooses a track inside the video.
3. When you hear a sentence you want to learn, tap **Save**. With a subtitle file the current
   line is saved directly; otherwise choose the start and end time. The audio goes to Box 1.
4. From the home screen, tap **Review**. Listen, reveal the text, then answer:
   - **I knew it** moves the card up one box.
   - **Didn't know** moves it back to Box 1.

Review intervals: Box 1 every day, Box 2 every 2 days, Box 3 every 4, Box 4 every 8, Box 5 every 16.

## Limitations

- The APK is built for 64-bit ARM phones (arm64-v8a) only.
- For subtitle tracks inside the video, the app times each line from when it appears and
  disappears on screen, and reads its text with on-device text recognition (ML Kit).
  The recognised text can contain small mistakes; check it before saving.
- Without any subtitles, Save looks at the audio from 8 s before to 6 s after the moment you
  tap it and finds the pauses around the sentence with Silero VAD (on the device). Speech
  without pauses, or very long sentences, may need the times adjusted by hand.

## Fonts

Archivo and Atkinson Hyperlegible are used under the SIL Open Font License, and the Silero VAD
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
