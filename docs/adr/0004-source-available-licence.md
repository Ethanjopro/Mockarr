# ADR 0004 — Public, source-available repository under PolyForm Strict 1.0.0

Date: 2026-09-08 · Status: **accepted** (Ethan: make the repo public "with a private license so
they can view it").

## Context
ADR 0001 left the licence and the openness stance undecided and kept the repo private until a
licence was picked. ADR 0002 chose Google Play as the primary channel and a free core with a later
Pro unlock; that model does not depend on the source being open, and it argues against a copyleft
or permissive licence that would let anyone republish the app.

The immediate need is different from distribution: Ethan wants employers and reviewers to be able
to read the code and the project history from a link. GitHub has no view-only sharing for private
repositories (collaborators on a personal repo get write access), so the practical options were a
private mirror with invites, a snapshot handed over out of band, or a public repository under a
licence that grants reading but not reuse.

What the repo contains that matters for going public (full-history scan, 2026-09-08): no API key,
keystore, `secrets.properties`, `local.properties` or token has ever been committed (all are
git-ignored and were checked against `git log --all -p`); the commit author email is present on
every commit; PROGRESS.md and `docs/release/play-store-recommendations.md` record personal
decisions (Play account type, LLC and tax notes) in Ethan's own words; and
`docs/design/refs/strava/` holds 42 curated Strava screenshots captured via Mobbin, which are
third-party copyrighted material under Mobbin's terms (the 600 MB raw set is git-ignored; the
curated set was committed on 2026-08-28).

## Decision
1. **The repository becomes public under the PolyForm Strict License 1.0.0.** `LICENSE` carries
   the official plain text verbatim under a short copyright header naming Ethan Jones as the
   licensor. PolyForm Strict grants a copyright and patent licence to use the software for
   noncommercial purposes (personal use, study, research, noncommercial organisations) and grants
   **no** right to distribute it, modify it, or make new works based on it. It is source-available,
   not open source, and is not OSI-approved.
2. **The "no open source / no F-Droid claims" rule from ADR 0001 stands.** README, CONTRIBUTING,
   PRODUCT.md and CLAUDE.md say "source-available" and never "open source". Store copy is
   unchanged (it never made the claim).
3. **Contributions:** issues and device-specific bug reports remain welcome; pull requests are not
   accepted while the licence forbids derived works. CONTRIBUTING.md says so and its old "whichever
   OSI-approved licence the project adopts" clause is removed.
4. **Third-party design references:** the curated Strava captures are removed from the working
   tree in the same commit that adds the licence, and `docs/design/refs/refs.md` keeps the table of
   what each frame informed. They remain in git history unless Ethan asks for a history rewrite,
   which is a separate, explicit decision (force-pushes are on the tool deny list).
5. **Personal context in the docs stays.** PROGRESS.md and the release memo are part of the
   project record Ethan wants readable; nothing in them is a credential.
6. **Relicensing later is possible in one direction only.** Ethan holds all copyright (no outside
   contributions have been merged), so a later switch to an OSI licence needs only a new ADR and
   a new `LICENSE`. Code accepted under PolyForm Strict from others would complicate that, which is
   another reason PRs are not accepted.

## Consequences
- `LICENSE` replaced; README "License" and "Contributing" sections rewritten; CONTRIBUTING.md
  rewritten around issues-only; PRODUCT.md and CLAUDE.md stop saying "repo private until the
  licence is chosen".
- `docs/design/refs/strava/*.webp` deleted from the tree (`refs.md` stays).
- The GitHub repository visibility flips to public after CI is green on the licence commit.
- Play distribution (ADR 0002) is unaffected: the app remains free on Play; a Pro unlock is still
  possible because the licence does not oblige source publication of anything.
- Deferred: history rewrite for the Strava captures (only on request); an OSI licence (only if the
  product direction changes); LLC and account questions from ADR 0002.
