#!/bin/sh
set -eu

if [ "${1:-}" != -i ]; then
    exec /usr/bin/ffmpeg "$@"
fi

source=$2
for output do :; done
case "$source:$output" in
    /voice-tmp/whisper-server-*.wav:/voice-tmp/whisper-server-*.wav_temp.wav) ;;
    *) exit 1 ;;
esac

if ! timeout 15s /usr/bin/ffmpeg -nostdin -loglevel error -protocol_whitelist file,pipe \
    -i "$source" -y -t 30 -ar 16000 -ac 1 -c:a pcm_s16le "$output"; then
    rm -f -- "$source" "$output"
    exit 1
fi
