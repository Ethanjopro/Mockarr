#!/bin/bash
# UserPromptSubmit hook: stamp the turn start time (per session) so the Stop
# hook can speak only after long turns instead of after every reply.
sid=$(/usr/bin/python3 -c 'import json,sys
try: print(json.load(sys.stdin).get("session_id","default"))
except Exception: print("default")' 2>/dev/null)
date +%s > "${TMPDIR:-/tmp}/mockarr_turn_${sid}" 2>/dev/null
exit 0
