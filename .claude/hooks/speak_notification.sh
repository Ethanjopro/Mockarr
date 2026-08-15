#!/bin/bash
# Notification hook: audible ping when Claude needs input (permission prompt,
# idle waiting) — the moments worth coming back to the terminal for.
say "Mockarr agent needs your input" >/dev/null 2>&1 &
exit 0
