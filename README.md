# Lingua Player

An Android video player for language learning.

- Plays local video/audio files and direct links or streams (MP4, MKV, WebM, HLS `.m3u8`, DASH `.mpd`).
- Shows subtitles from an external file (SRT or VTT) or from a subtitle track inside the video.
- **Save line**: saves the sentence being spoken as a card with its audio clip and subtitle text.
- **Learning box**: a 5-box Leitner system for reviewing saved sentences.

Built with Kotlin, Jetpack Compose and Media3 (ExoPlayer + Transformer).

## Install

Every push to `main` builds an APK with GitHub Actions and publishes it on the
[Releases](../../releases) page. Download `LinguaPlayer.apk` on your phone and open it
(you may need to allow "Install unknown apps" for your browser or file manager).

## How to use

1. Open a video file, or paste a link and tap **Play link**.
2. Tap **Load subtitles** to pick an `.srt` or `.vtt` file, or choose a built-in subtitle
   track with the CC button in the player controls.
3. When you hear a sentence you want to learn, tap **Save line**. Its audio is cut and saved
   to Box 1.
4. From the home screen, tap **Review**. Listen, reveal the text, then answer:
   - **I knew it** moves the card up one box.
   - **Didn't know** moves it back to Box 1.

Review intervals: Box 1 every day, Box 2 every 2 days, Box 3 every 4, Box 4 every 8, Box 5 every 16.

## Limitations

- Old formats such as AVI are not supported.
- AC3/DTS audio (found in some MKV files) only plays and clips on devices that have those decoders.

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
