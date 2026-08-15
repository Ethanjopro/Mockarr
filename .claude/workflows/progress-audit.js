// Docs-drift audit: find claims in the project docs that no longer match the
// actual repo. Shape: one scanner per doc extracts repo-checkable claims, then
// independent skeptics try to REFUTE each claim against the codebase itself.
// Refute-by-default is what keeps plausible-but-wrong findings out of the
// result: an unverified claim is not a stale claim.
export const meta = {
  name: 'progress-audit',
  description: 'Find claims in PROGRESS.md / README.md / CLAUDE.md / the playbook that no longer match the repo',
  phases: [
    { title: 'Scan', detail: 'one agent per doc extracts repo-checkable claims' },
    { title: 'Verify', detail: 'independent skeptics refute each claim against the codebase' },
  ],
}

const DEFAULT_TARGETS = ['PROGRESS.md', 'README.md', 'CLAUDE.md', 'docs/claude-code-playbook.md']
const targets = Array.isArray(args) && args.length ? args : DEFAULT_TARGETS
const CHUNK = 6

const CLAIMS_SCHEMA = {
  type: 'object',
  required: ['claims'],
  properties: {
    claims: {
      type: 'array',
      items: {
        type: 'object',
        required: ['claim', 'line'],
        properties: {
          claim: { type: 'string', description: 'verbatim quote of the checkable claim' },
          line: { type: 'integer', description: '1-indexed line number' },
          checks: { type: 'string', description: 'what in the repo would confirm or refute it' },
        },
      },
    },
  },
}

const VERDICT_SCHEMA = {
  type: 'object',
  required: ['verdicts'],
  properties: {
    verdicts: {
      type: 'array',
      items: {
        type: 'object',
        required: ['claim', 'line', 'stale', 'reason'],
        properties: {
          claim: { type: 'string' },
          line: { type: 'integer' },
          stale: { type: 'boolean', description: 'true ONLY if provably wrong against the current repo' },
          reason: { type: 'string' },
          evidence: { type: 'string', description: 'file:line or command output that settles it' },
          correction: { type: 'string' },
        },
      },
    },
  },
}

phase('Scan')
log(`Auditing ${targets.length} docs for drift against the repo`)

const results = await pipeline(
  targets,
  file =>
    agent(
      `Read ${file} in this repo. Extract the claims that the CODEBASE ITSELF can confirm or refute ` +
        `and that could silently go stale: file paths, module/class/function names, gradle tasks and ` +
        `commands, settings/preference keys, thresholds and version numbers, and statements that a ` +
        `feature works a specific way. Skip prose, opinion, history framed as history ("in session 3 ` +
        `we did X" is a record, not a claim about today), and anything only external services could ` +
        `verify. Quote each claim verbatim with its 1-indexed line number. Return the ~12 most ` +
        `load-bearing claims, prioritizing ones current work would rely on.`,
      { label: `scan:${file}`, phase: 'Scan', schema: CLAIMS_SCHEMA, effort: 'low' },
    ),
  (scan, file) => {
    const claims = (scan && scan.claims) || []
    const chunks = []
    for (let i = 0; i < claims.length; i += CHUNK) chunks.push(claims.slice(i, i + CHUNK))
    return parallel(
      chunks.map((chunk, idx) => () =>
        agent(
          `You are a skeptic auditing documentation claims from ${file} against the ACTUAL repo ` +
            `(use Read/Glob/Grep; scripts/gradle for task lists if needed; never the web). For each ` +
            `claim below, try to REFUTE it. Set stale:true ONLY if you can show it is wrong against ` +
            `the current tree and cite the file:line or command output that settles it. If you cannot ` +
            `verify a claim either way, set stale:false — an unverified claim is not a stale claim, ` +
            `and a false report costs more than a missed one. Claims:\n\n` +
            chunk.map(c => `- (L${c.line}) "${c.claim}"`).join('\n'),
          { label: `verify:${file}#${idx + 1}`, phase: 'Verify', schema: VERDICT_SCHEMA },
        ).then(v => ({ file, verdicts: (v && v.verdicts) || [] })),
      ),
    )
  },
)

const flat = results
  .filter(Boolean)
  .flat()
  .filter(Boolean)
const stale = flat
  .flatMap(r => r.verdicts.map(v => ({ file: r.file, ...v })))
  .filter(v => v.stale)
  .sort((a, b) => (a.file === b.file ? a.line - b.line : a.file.localeCompare(b.file)))

const checked = flat.reduce((n, r) => n + r.verdicts.length, 0)
log(`Checked ${checked} claims; ${stale.length} stale`)
return { docsAudited: targets.length, claimsChecked: checked, stale }
