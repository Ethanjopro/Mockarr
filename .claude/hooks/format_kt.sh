#!/bin/bash
# PostToolUse hook (Edit|Write): auto-format the touched Kotlin file so ktlint
# violations never reach the build. Uses the standalone ktlint pinned to 1.5.0
# (parity with detekt-formatting via .editorconfig). Fail-silent by design —
# a formatting hiccup must never block an edit.
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export PATH="$JAVA_HOME/bin:$PATH"

file=$(/usr/bin/python3 -c 'import json,sys
try: print(json.load(sys.stdin).get("tool_input",{}).get("file_path",""))
except Exception: pass' 2>/dev/null)

case "$file" in
  *.kt|*.kts)
    ktlint_bin="$HOME/.local/bin/ktlint"
    [ -x "$ktlint_bin" ] && [ -f "$file" ] && "$ktlint_bin" -F "$file" >/dev/null 2>&1
    ;;
esac
exit 0
