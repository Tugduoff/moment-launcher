#!/usr/bin/env bash
# Plays the demo story on a device (emulator or phone over adb) and records the screen.
# Usage: scripts/demo_run.sh [adb-serial]    → writes demo_raw.mp4 in the current folder.
# Muse calls take 3-20 s, so the footage has pauses: speed them up in the edit.
set -euo pipefail
A="adb ${1:+-s $1}"
demo() { $A shell am start -n app.moment.launcher/.MainActivity --es demo "'$1'" >/dev/null 2>&1; }
type_slowly() { local t="$1" q=""; for ((i=0; i<${#t}; i++)); do q+="${t:$i:1}"; demo "query:$q"; sleep 0.07; done; }

$A shell input keyevent KEYCODE_HOME
demo "reset;clock:2026-10-27-08-47;place:HOME"; sleep 12          # let Muse sort the apps first
$A shell screenrecord --bit-rate 12000000 --time-limit 180 /sdcard/moment_demo.mp4 &
REC=$!; sleep 1
T0=$(python3 -c 'import time;print(time.time())'); : > demo_markers.txt
mark() { python3 -c "import time;print(f'{time.time()-$T0:.2f} $1')" >> demo_markers.txt; }

# 1. Calm morning: asleep, breathing, z's.
mark morning; sleep 5; demo "tap"; sleep 4
# 2. Library: Focus, glasses, distracting apps hidden, 10-second wait.
mark library; demo "clock:2026-10-27-15-30;place:LIBRARY"; sleep 6; demo "openanyway"; sleep 4
# 3. Noon at 17 bd Jourdan: the companion has an idea → Muse Spark drafts → accept → Muse Image dresses it.
mark noon; demo "seed;clock:2026-10-27-11-58;place:JOURDAN"; sleep 5
mark idea; demo "tap"; sleep 10; mark accept; demo "accept"; sleep 30
# 4. Talk to it.
mark talk; demo "clock:2026-10-27-17-30;place:ELSEWHERE"; sleep 2
type_slowly "Put on a red cowboy hat"; sleep 1; demo "ask:Put on a red cowboy hat"; sleep 28
mark guitar; type_slowly "Now play the guitar"; sleep 1; demo "ask:Now play the guitar"; sleep 26
# 5. Car, night out, game, rain.
mark car; demo "clock:2026-10-27-18-05;place:HOME;car:1"; sleep 7
mark night; demo "car:0;clock:2026-10-30-23-30;place:AWAY"; sleep 6
mark game; demo "place:HOME;clock:2026-10-30-21-40;hp:1"; sleep 6
mark rain; demo "hp:0;clock:2026-11-01-16-20;rain:1"; sleep 6
mark end; demo "rain:0;live"; sleep 3

kill -INT $REC 2>/dev/null || true; sleep 2
$A pull /sdcard/moment_demo.mp4 demo_raw.mp4 && echo "Saved demo_raw.mp4"
