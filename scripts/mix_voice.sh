#!/usr/bin/env bash
# Lays one voice file per scene (vo/01.* … vo/13.*) over the exported film, at each scene's start,
# and ducks the film's music under the voice. Usage: scripts/mix_voice.sh film.mp4 vo/ out.mp4 [ffmpeg]
set -euo pipefail
FILM=$1; VO=$2; OUT=$3; FF=${4:-ffmpeg}
STARTS=(0 10 20 35 50 68 85 102 115 130 142 155 170)
inputs=(-i "$FILM"); filters=""; mix=""; n=0
for i in $(seq 1 13); do
  f=$(ls "$VO"/$(printf %02d $i).* 2>/dev/null | head -1 || true)
  [ -z "$f" ] && continue
  n=$((n+1)); inputs+=(-i "$f")
  ms=$(( ${STARTS[$((i-1))]} * 1000 ))
  filters+="[$n:a]aresample=48000,aformat=channel_layouts=stereo,adelay=${ms}|${ms},apad[v$n];"
  mix+="[v$n]"
done
[ $n -eq 0 ] && { echo "No voice files in $VO"; exit 1; }
filters+="${mix}amix=inputs=$n:normalize=0:duration=longest,volume=1.6[voice];"
filters+="[voice]asplit=2[vk][vm];"
filters+="[0:a]aresample=48000[music];[music][vk]sidechaincompress=threshold=0.02:ratio=8:attack=20:release=400[ducked];"
filters+="[ducked][vm]amix=inputs=2:normalize=0:duration=first,alimiter=limit=0.95[a]"
"$FF" -y "${inputs[@]}" -filter_complex "$filters" -map 0:v -map "[a]" -c:v copy -c:a aac -b:a 192k -shortest "$OUT"
echo "Wrote $OUT"
