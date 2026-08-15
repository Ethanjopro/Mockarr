---
name: five
description: Five Whys root-cause analysis for a bug, flaky verification, or CI/local divergence
argument-hint: "[issue description]"
---

Apply the Five Whys technique to: $ARGUMENTS

1. State the problem precisely (observed behavior vs expected, and where it was
   observed — emulator, unit test, CI, physical device).
2. Ask "why did this happen?" and answer with evidence from the repo, logs, or
   a reproduction — not speculation. Repeat on each answer.
3. Five is a guide, not a quota: stop when you reach a cause that, if fixed,
   prevents the whole chain — and keep going past five if you haven't.
4. Multiple root causes may exist; branch when the evidence genuinely splits.
5. Validate by working backwards: does the chain re-derive the observed symptom?
6. Distinguish a symptom in the UI layer from a cause in state/DI/lifecycle
   wiring — Compose recomposition often displays a bug that lives elsewhere.

The final answer MUST name a specific file/line, config key, or command as the
root cause, plus the fix that addresses the root (not the symptom). If the
evidence doesn't reach that specificity, say what experiment would settle it
instead of guessing.
