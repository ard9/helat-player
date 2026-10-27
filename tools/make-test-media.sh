#!/usr/bin/env bash
# Recreates the small media files used by the unit tests (app/src/test/resources/media).
# Needs ffmpeg. The files are committed, so this only needs to run when they must change.
set -euo pipefail
out="$(cd "$(dirname "$0")/.." && pwd)/app/src/test/resources/media"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir -p "$out"

cat > "$work/en.srt" <<'SRT'
1
00:00:02,000 --> 00:00:04,500
Hello there.

2
00:00:05,000 --> 00:00:08,200
<i>How are you</i> doing today?

3
00:00:20,100 --> 00:00:23,900
I'll be right back.
Wait for me.

4
00:00:50,000 --> 00:00:52,000
The end &amp; more.
SRT

cat > "$work/fa.srt" <<'SRT'
1
00:00:02,000 --> 00:00:04,500
سلام.

2
00:00:05,100 --> 00:00:08,000
امروز حالت چطوره؟

3
00:00:20,100 --> 00:00:23,900
زود برمی‌گردم.
SRT

ffmpeg -v error -y -i "$work/en.srt" "$work/en.ass"

# 60 s of a tiny video with three subtitle tracks; cues (the index) at the end.
ffmpeg -v error -y -f lavfi -i testsrc=size=64x36:rate=5:duration=60 \
  -i "$work/en.srt" -i "$work/fa.srt" -i "$work/en.ass" \
  -map 0 -map 1 -map 2 -map 3 -c:v libx264 -crf 45 -g 25 \
  -c:s:0 srt -c:s:1 srt -c:s:2 ass \
  -metadata:s:s:0 language=eng \
  -metadata:s:s:1 language=per -metadata:s:s:1 title=Persian \
  -metadata:s:s:2 language=eng -metadata:s:s:2 title="Styled" \
  -fflags +bitexact "$out/subs.mkv"

# Same content with the cues at the front of the file.
ffmpeg -v error -y -i "$out/subs.mkv" -map 0 -c copy -cues_to_front 1 -reserve_index_space 0 \
  -fflags +bitexact "$out/cues-front.mkv"

# Live-style file: no cues at all.
ffmpeg -v error -y -i "$out/subs.mkv" -map 0 -c copy -live 1 -f matroska \
  -fflags +bitexact "$out/no-cues.mkv"

# WebM with a WebVTT track.
ffmpeg -v error -y -f lavfi -i testsrc=size=64x36:rate=5:duration=60 -i "$work/en.srt" \
  -map 0 -map 1 -c:v libvpx -b:v 20k -c:s webvtt -fflags +bitexact "$out/subs.webm"

ls -l "$out"
