---
description: Audit a CLAUDE.md file for the patterns that actually degrade Claude Code's output — vagueness, unnamed files, stale facts, and bloat. Use when asked to review, audit, improve, shrink, or fix a CLAUDE.md, and when the project's results feel inconsistent or Claude keeps rediscovering the same context.
argument-hint: "[path to CLAUDE.md, defaults to ./CLAUDE.md]"
allowed-tools: Read, Glob, Grep
---

# CLAUDE.md review

CLAUDE.md loads into context on every session: every line is either paying
rent or costing tokens forever. Most "the model isn't following instructions"
problems are instruction problems.

## Steps
1. Read the target (default `./CLAUDE.md`; also check `~/.claude/CLAUDE.md`
   and nested `**/CLAUDE.md` — the closest one wins).
2. Score each dimension below, quoting the failing lines.
3. Propose concrete rewrites, not "consider being more specific."
4. Do not edit the file unless asked. Report first.

## What to check
- **Specificity** (highest leverage): flag unfalsifiable directives ("write
  clean code", "be careful"). Every rule should be checkable by reading a
  diff — "refactor functions over 40 lines" is checkable; "keep functions
  short" is not.
- **Named anchors**: rules referencing "the config" or "the usual pattern"
  force rediscovery. Verify every path, command, and filename still exists —
  a CLAUDE.md pointing at a deleted file actively misleads.
- **Staleness**: cross-check build/test/lint commands and versions against
  `build.gradle.kts`, `gradle/libs.versions.toml`, `config/detekt/detekt.yml`,
  and `scripts/` — a wrong build command is worse than none.
- **Bloat and rent**: anything derivable from code (file trees, dependency
  lists) goes. Long procedures for occasional tasks belong in a skill, whose
  body loads only when used.
- **Conflicts**: contradictory rules; precedence surprises from nested or
  user-level CLAUDE.md files.
- **Missing**: how to build/test/lint (the self-verification trio); untouchable
  dirs or generated files; genuinely surprising conventions.

## Output format
```
## CLAUDE.md review — <path> (<N> lines)

**Verdict:** <one sentence>

### Blocking
- L<n>: <quoted line> → <concrete rewrite>

### Worth fixing
- L<n>: <quoted line> → <concrete rewrite>

### Delete (costs context every session, earns nothing)
- L<n>–<m>: <what and why>

### Missing
- <gap> → <suggested line to add>

**Estimated size after edits:** <N> lines (from <M>)
```

## Rules
- Quote real line numbers and real text. Never invent a finding to fill a
  section; if a section has no findings, write `None.`
- Prefer deleting to rewriting.
