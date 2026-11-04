#!/usr/bin/env bash
# Captures README screenshots of every mode. Usage: scripts/screenshots.sh [adb-serial]
set -uo pipefail
A="adb ${1:+-s $1}"; D="$(dirname "$0")/../docs/screens"; mkdir -p "$D"
demo() { $A shell am start -n app.moment.launcher/.MainActivity --es demo "'$1'" >/dev/null 2>&1; }
snap() { $A exec-out screencap -p > "$D/$1.png"; sips -Z 1200 "$D/$1.png" >/dev/null; echo "$1"; }
$A shell input keyevent KEYCODE_HOME; sleep 1
demo "reset;clock:2026-10-27-08-47;place:HOME"; sleep 14; snap 01-home-asleep
demo "seed;clock:2026-10-27-11-58;place:JOURDAN"; sleep 6; snap 02-idea
demo "tap"; sleep 11; snap 03-proposal
demo "accept"; sleep 40; snap 04-uni-restaurant-dressed
demo "clock:2026-10-27-15-30;place:LIBRARY"; sleep 6; snap 05-study
demo "clock:2026-10-27-18-05;place:HOME;car:1"; sleep 6; snap 06-car
demo "car:0;clock:2026-10-30-21-40;hp:1"; sleep 6; snap 07-game
demo "hp:0;clock:2026-11-01-16-20;rain:1"; sleep 6; snap 08-rain
demo "rain:0;clock:2026-10-30-23-30;place:AWAY"; sleep 6; snap 09-night
demo "clock:2026-10-27-17-00;place:ELSEWHERE;ask:put on a red cowboy hat"; sleep 34
demo "ask:play the guitar for me"; sleep 36; snap 10-wardrobe
