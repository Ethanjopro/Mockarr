#!/bin/bash
# SessionStart hook: stdout is injected into Claude's context at session start/resume.
# Gives a fresh session everything it needs to pick up where the last one stopped.
cd "${CLAUDE_PROJECT_DIR:-.}" 2>/dev/null || exit 0

echo "## Mockarr session context (auto-injected by .claude/hooks/session_start.sh)"
echo "Branch: $(git branch --show-current 2>/dev/null) | dirty files: $(git status --porcelain 2>/dev/null | wc -l | tr -d ' ')"
echo "Last commit: $(git log -1 --format='%h %s (%cr)' 2>/dev/null)"

# Latest CI status, bounded to 5s so an offline start never hangs the session.
if command -v gh >/dev/null 2>&1; then
  ci=$(gh run list -L1 --json status,conclusion,displayTitle \
        -q '.[0] | .status + " " + .conclusion + " — " + .displayTitle' 2>/dev/null &
       ghpid=$!
       (sleep 5 && kill "$ghpid" 2>/dev/null) >/dev/null 2>&1 &
       wait "$ghpid" 2>/dev/null)
  [ -n "$ci" ] && echo "Latest CI: $ci"
fi

echo
echo "### PROGRESS.md — latest entry (tail)"
tail -n 50 PROGRESS.md 2>/dev/null
exit 0
