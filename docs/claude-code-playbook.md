# Claude Code playbook for Mockarr

The distilled result of the August 2026 tooling research round: what we
adopted, what we rejected and why, and how to drive Claude Code efficiently on
this project. Sources: the wesammustafa/Claude-Code-Everything-You-Need-to-Know
repo and ~40 of its linked resources, cross-checked against practitioner
evidence (Hacker News, engineering blogs, GitHub issue trackers; Reddit/X were
unreachable, so framework fan communities are represented mainly by critics).

## What we adopted (and where it lives)

| Piece | Where | Why |
|---|---|---|
| Project instructions | `CLAUDE.md` | Loads every session; encodes build/emulator/lint/process rules that were previously re-derived per session |
| Permissions + hook wiring | `.claude/settings.json` | Unattended rounds without permission fatigue; deny-list guardrails |
| JDK-pinned gradle wrapper | `scripts/gradle` | One prefix-matchable command; no more `export JAVA_HOME && ./gradlew` compounds |
| Emulator verification skill | `.claude/skills/emulator-verify/` | Nine sessions of emulator gotchas as standing instructions + a triaged report format (incl. dark-theme and font-scale passes adapted from the Impeccable skill's Android reference) |
| Kotlin conventions skill | `.claude/skills/kotlin-conventions/` | detekt tripwires, Compose pitfalls, test style; loads only when `.kt` files are touched |
| CLAUDE.md audit skill | `.claude/skills/claude-md-review/` | Keeps CLAUDE.md from rotting; run every few rounds |
| Session-start context injection | `.claude/hooks/session_start.sh` | Fresh sessions start knowing the PROGRESS.md tail, branch, and CI status |
| Format-on-edit | `.claude/hooks/format_kt.sh` + `.editorconfig` + standalone ktlint 1.5.0 (`~/.local/bin/ktlint`) | ktlint violations fixed at edit time, never at build time; `.editorconfig` pins `intellij_idea` style for parity with detekt-formatting (verified: 0 violations on the clean tree) |
| Audio pings (macOS `say`) | `.claude/hooks/speak_stop.sh` (turns ≥45 s), `speak_notification.sh` | Signals "done"/"needs input" during unattended stretches; delete the hook entries in settings.json to disable |

Tier 3, adopted in the follow-up round (all trialed live before keeping):

| Piece | Where | Trial result |
|---|---|---|
| Docs-drift audit workflow | `.claude/workflows/progress-audit.js` (invoke: `/progress-audit`) | First run: 15 agents, 54 claims checked, 1 genuinely stale claim found, 0 false positives — the refute-by-default framing held. Run every ~5 rounds |
| `build-fixer` subagent | `.claude/agents/build-fixer.md` | Distills Gradle failures to `file:line — rule — message`; never edits tests to make them pass |
| `emulator-verifier` subagent | `.claude/agents/emulator-verifier.md` | Live trial PASSed (3-tab smoke pass, screenshot evidence); its friction feedback drove the emu.sh improvements below |
| `/five` root-cause command | `.claude/commands/five.md` | Five Whys; the final answer must name a file/line or config key |
| emu.sh extensions | `scripts/emu.sh` — `launch`, `waitfor` (poll + echoes what it matched, `"a|b"` alternation), `assert`, `matchtext`, real usage text | All paths verified on the live emulator, including timeout and negative cases |
| Impeccable (native half) | `.claude/skills/impeccable/` (web-only `scripts/` gitignored — reinstall with `npx impeccable install`) | First audit scored the app 13/20 and surfaced real defects our linters can't model (TalkBack-unreachable tooltip descriptions, dark-UI/light-map pairing, a per-tick recomposition leak). Its raw-hex and top-app-bar rules fight deliberate choices on the MapLibre canvas — overrule the rubric there. Re-run `/impeccable audit` after design work |

## What we rejected, and why

- **SuperClaude / BMAD** — team-ceremony frameworks (personas, requirement
  handoffs). Context tax is measured (~22% of a window in one user's report);
  BMAD users report 12–16 h of ceremony before the first line of code. Solo
  work with plan mode already covers the need.
- **Agent Teams** — solo practitioners independently report 3–4× token cost,
  stuck teammates, over-optimistic status reports. Worth revisiting only for
  competing-hypothesis debugging of a bug we've failed to isolate twice.
- **mobile-mcp** (and adb MCP servers generally) — no firsthand Android success
  stories found; open bugs in the accessibility-tree and app-launch paths. The
  documented Android QA successes use raw adb + UI Automator — which is what
  `scripts/emu.sh` already is. Extend emu.sh instead.
- **Memory MCP** (PROGRESS.md + auto-memory already cover it, with fewer moving
  parts), **Sequential-Thinking MCP** (redundant with plan mode), **Playwright
  MCP** (no web frontend), **Serena** (Kotlin is its weakest LSP tier).
- **Third-party Android skill packs** — small hobby repos that drift from our
  exact AGP/Kotlin versions; mined for ideas, never installed.

## Driving guide (for Ethan)

**Session lifecycle — the highest-value habit.** One context per task/feedback
round. The session-start hook means a fresh terminal already knows where we
left off, so fresh starts are cheap. Rules of thumb:
- New round → new session (or `/clear`). Don't clear mid-task.
- If you've corrected Claude twice on the same thing, `/clear` and restate the
  task sharper — a clean session with a better prompt beats a long session
  with accumulated corrections (the wrong path keeps biasing everything after).
- `/compact <focus>` condenses without losing the thread; `/resume` recovers a
  session you cleared by mistake.

**Model & effort.** Current choice: Fable 5 for everything (deliberate).
Alternatives if cost/limits ever bite: `/model opusplan` (Opus plans, Sonnet
executes) or Sonnet + `"advisorModel": "opus"` (Anthropic-measured: better and
~12% cheaper than Sonnet alone; unavailable while Fable is the main model —
an advisor must be at least as capable as the executor). Pick the model at
session start; mid-session switches invalidate the prompt cache. Leave effort
at `high`; `ultrathink` in a prompt is the only recognized deep-thinking
keyword ("think hard" is dead lore). Skip fast mode — our rounds wait on
Gradle and emulator boots, not token speed.

**Prompting patterns that work here.**
- Feedback rounds as `/plan` with the raw notes pasted in — works well, keep
  doing it. Number the items if you want per-item answers.
- Say "verify on the emulator" to trigger the verification skill's full
  playbook (or `/emulator-verify <feature>` explicitly).
- Before a commit you're unsure about: "run /code-review on the diff first."
- Big mechanical sweeps (rename across modules, migration): say "use a
  workflow" to opt into orchestration — otherwise it stays single-agent.
- Every ~5 rounds: `/progress-audit` (docs-drift check) and `/claude-md-review`.
- "Have build-fixer handle the build" / "have emulator-verifier check X" keeps
  Gradle stacktraces and adb output out of the main conversation.
- `/five <symptom>` when a bug resists the first fix; `/impeccable audit` after
  UI-heavy rounds (expect it to fight the MapLibre canvas colors — that's fine).
- Ask "what's the efficiency note for this round?" anytime; Claude is under
  standing instructions to volunteer one when there's something worth saying.

**Maintenance.** Run `/claude-md-review` every few rounds. When a correction
happens mid-round, expect it to be promoted into CLAUDE.md or the conventions
skill — that's by design (the feedback-capture rule).
