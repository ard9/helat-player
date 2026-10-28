# AGENTS.md

Entry point for coding agents (and people) working on Helat Player. Read this file fully,
then read the docs for the area you are going to change. User documentation is in `README.md`.

## What the app is

An Android video player for language learning. The owner watches films (mostly MKV files and
direct online links, often HEVC 10-bit with Persian and English subtitle tracks) and saves
spoken lines as flashcards: audio clip + video frame + text + meaning, reviewed with a
5-box Leitner system. The app is in English and Persian.

The owner talks to agents in Persian; reply in the language they use.

## Working agreement

These rules come from the owner and apply to every change:

1. **Agree before building.** For anything non-trivial, restate the task, ask only questions
   that change the result (with a suggested default), outline the plan, and wait for approval.
2. **Build only what was agreed.** No speculative options, flags or abstractions. Error
   handling, input validation and secure defaults are part of the job, not extras.
3. **Ideas are proposals.** Describe an improvement (benefit and cost) and implement it only
   after approval.
4. **Always flag security issues**, in new or existing code: what, where, impact, severity
   (Critical/High/Medium/Low, honestly), and the proposed fix. Do not change existing code to
   fix them without approval.
5. **All file content is English**: code, comments, docs, commit messages, UI strings.
   The one exception is the Persian translation, `res/values-fa/strings.xml`.
6. **Commits**: authored as `ard9 <ard9@users.noreply.github.com>`. Do **not** add
   `Co-Authored-By`, session links or other tool trailers; the owner asked for them removed.

## Build and verify (short)

GitHub Actions is the build: every push to `main` runs the unit tests, then builds the APK and
publishes the release `build-<run number>`. A failing test stops the build. There is no local
Android SDK. Agents usually cannot run the app: always say what was verified (tests, CI) and
what was not (behaviour on the phone). Details: `docs/build-and-release.md`.

## Where to read before you change something

| Before working on | Read |
| --- | --- |
| Anything (current state, open items, what is verified on the phone) | `docs/status.md` |
| Where code lives, how parts connect, data on the device | `docs/architecture.md` |
| CI, signing, versions, toolchain pins, reading build errors | `docs/build-and-release.md` |
| Playback, controls, rotation, repeat/loop/speed, resume | `docs/player.md` |
| Saving a line: subtitles, MKV reading, speech detection, clipping | `docs/saving-lines.md` |
| Cards, Leitner review, backup and restore | `docs/cards-and-review.md` |
| Role-play | `docs/roleplay.md` |
| UI text, translation, right to left, the in-app guide | `docs/i18n.md` |
| Tests, fixtures, what is not covered | `docs/testing.md` |
| Permissions, untrusted input, secrets, open security items | `docs/security.md` |
| Why things are the way they are (do not undo without a reason) | `docs/decisions.md` |

## Keeping the docs useful

1. Read the doc of an area before changing it.
2. A change to behaviour, structure or a decision updates the matching doc **in the same
   commit**. A user-visible change also updates `README.md` and the in-app guide.
3. Each fact lives in one doc; others link to it instead of repeating it.
4. Keep each doc under 200 lines (`DocsTest` fails otherwise). When one grows, split it and
   add the new file to the table above. Every file in the table must exist (also tested).
5. At the end of a task, update `docs/status.md`: what changed, what is verified on the
   phone, and what is open.
