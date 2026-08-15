#!/bin/bash
# Stop hook: audible ping when a long turn finishes (builds, emulator rounds, CI
# watches) so Ethan can walk away from the terminal. Quick exchanges stay silent.
threshold=45

sid=$(/usr/bin/python3 -c 'import json,sys
try: print(json.load(sys.stdin).get("session_id","default"))
except Exception: print("default")' 2>/dev/null)

stamp_file="${TMPDIR:-/tmp}/mockarr_turn_${sid}"
[ -f "$stamp_file" ] || exit 0
start=$(cat "$stamp_file" 2>/dev/null)
now=$(date +%s)
if [ -n "$start" ] && [ $((now - start)) -ge "$threshold" ]; then
  say "Mockarr agent is done" >/dev/null 2>&1 &
fi
exit 0
