#!/usr/bin/env bash
# Generates the Moment companion's expressions with Muse Image (muse-image-1.0).
# 1. base.png is generated from a text prompt.
# 2. Every expression is an EDIT of base.png, so the character stays identical.
# Usage: MODEL_API_KEY=... ./art/generate_companion.sh   (writes art/raw/*.png)
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p raw
: "${MODEL_API_KEY:?set MODEL_API_KEY}"
API=https://api.meta.ai/v1

decode() { python3 -c "
import json,base64,sys
t=open(sys.argv[1]).read(); d=json.loads(t)
if 'error' in d: sys.exit('error: '+str(d['error']))
open(sys.argv[2],'wb').write(base64.b64decode(d['data'][0]['b64_json']))" "$1" "$2"; }

if [ ! -f raw/base.png ]; then
  curl -s "$API/images/generations" -H "Authorization: Bearer $MODEL_API_KEY" -H "Content-Type: application/json" \
    -d '{"model":"muse-image-1.0","n":1,"response_format":"b64_json","output_format":"png","prompt":"A cute minimal mascot character for a phone launcher: a small round yellow peach-like fruit (#FFD66B to #F7B84B soft gradient) with a single small green leaf on top, two small dark oval eyes, soft pink blush cheeks, tiny smile. Flat vector illustration, soft subtle shading, centered, front view, on a plain pure white background, no text, no outline, sticker style, lots of empty space around."}' \
    > raw/base.json
  decode raw/base.json raw/base.png
fi

KEEP="Same exact character, same style, same colors, same size and same position in the frame. Keep the plain white background."
gen() { # name, prompt
  [ -f "raw/$1.png" ] && return 0
  curl -s "$API/images/edits" -H "Authorization: Bearer $MODEL_API_KEY" \
    -F model=muse-image-1.0 -F image=@raw/base.png -F output_format=png \
    -F "prompt=$KEEP $2" > "raw/$1.json"
  decode "raw/$1.json" "raw/$1.png" && echo "done $1"
}

gen asleep "Change only the face: eyes closed as two gentle downward curved lines, peaceful sleeping, mouth a tiny relaxed line, and two small grey letters z floating above the top right." &
gen awake "Change only the face: eyes open and bright with a small white highlight in each eye, a happy small open smile, looking at the viewer." &
gen talking "Change only the face: eyes open, eyebrows slightly raised, mouth a small open round o shape as if saying something, curious and friendly." &
gen thinking "Change only the face: eyes looking up and to the right, mouth a small flat line, thinking, and three small grey dots floating above the top right." &
gen glasses "Add thin round dark brown reading glasses over the eyes, focused calm expression with a tiny smile, like studying." &
gen seatbelt "Add a dark grey car seatbelt strap going diagonally across the body from the top left to the bottom right with a small silver buckle, calm focused eyes looking forward." &
gen headphones "Add over-ear headphones on the head: the band over the top behind the leaf, one pink ear cup on the left side and one cyan ear cup on the right side, excited happy eyes and a big smile." &
gen sleepy "Change only the face: eyes closed as happy upward curves, content cozy smile, and a tiny light blue raindrop sitting on the leaf." &
wait
echo "All expressions in art/raw. Run art/process.py to cut them out for the app."
