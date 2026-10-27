# Build and release

There is no Gradle wrapper and no local Android SDK is assumed. **GitHub Actions is the build.**

## CI

`.github/workflows/build.yml` runs on every push to `main` (and by hand), on `ubuntu-24.04`
(a fixed image, not `ubuntu-latest`) with JDK 17 (Temurin) and Gradle 8.13. Actions are pinned
by commit SHA with the version in a comment; to update one, look up the new tag's commit
(`git ls-remote --tags https://github.com/<owner>/<action>.git`, use the `^{}` line for
annotated tags):

1. **Unit tests**: `gradle testDebugUnitTest`. A failing test stops the run, so no broken APK
   is published.
2. **Build**: a signed `release` APK if the signing secrets exist, otherwise a `debug` APK.
3. **Release**: every successful run publishes the GitHub Release `build-<run number>` with
   `LinguaPlayer.apk`. The owner installs from the release page on the phone.

Version: `versionCode` is the run number and `versionName` is `0.1.<run number>`
(`GITHUB_RUN_NUMBER`, see `app/build.gradle.kts`). The home screen shows the version.

## Reading a failed build

The step "Summarize build errors" puts compiler errors and failed tests (with assertion
messages) in a check-run annotation titled **Build errors**; the HTML test report is uploaded
as the `test-report` artifact.

```sh
RUN=$(gh api repos/ard9/lingua-player/actions/runs --jq '.workflow_runs[0].id')
gh api repos/ard9/lingua-player/actions/runs/$RUN --jq '.status, .conclusion'
JOB=$(gh api repos/ard9/lingua-player/actions/runs/$RUN/jobs --jq '.jobs[0].id')
gh api repos/ard9/lingua-player/check-runs/$JOB/annotations --jq '.[].message'
gh api repos/ard9/lingua-player/releases --jq '.[0].html_url'
```

Raw job logs may not be downloadable from agent sandboxes; use the annotation. In the agent
sandbox, `gh` cannot delete releases or set secrets; the owner does that by hand.

## Signing

Four repository secrets: `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`,
`SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`. The keystore is decoded to the runner's temp
folder and removed at the end. It is never committed (`.gitignore` blocks `*.jks`,
`*.keystore`, `signing-secrets*.txt`).

Changing the key forces users to uninstall and lose their cards, so never rotate it casually.

## Toolchain pins

libVLC 3.7 requires: AGP 8.11.1, Kotlin 2.2.10 (with the Compose plugin of the same version),
`compileSdk 36`. Also `targetSdk 35`, `minSdk 26`. Kotlin options use
`kotlin { compilerOptions { jvmTarget } }`.

Main libraries: `org.videolan.android:libvlc-all` 3.7.x, Media3 1.5.1 (exoplayer,
transformer), ONNX Runtime Android 1.20.0, WorkManager 2.10.1 (review reminder), Compose BOM 2024.12.01, `material-icons-core`
(not the extended set, to keep the APK small; extra icons are drawn in `ui/PlayerIcons.kt`).

The APK is **arm64-v8a only** to limit size (~80 MB: libVLC, ONNX Runtime, Media3).

## Commits

Authored as `ard9 <ard9@users.noreply.github.com>`, with no `Co-Authored-By` or other trailers:

```sh
git -c user.name=ard9 -c user.email=ard9@users.noreply.github.com commit -m "..."
```
