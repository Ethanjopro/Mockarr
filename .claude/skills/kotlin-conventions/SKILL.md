---
description: Mockarr's Kotlin/Compose conventions — the detekt rules this repo actually trips, Compose pitfalls already hit once, and unit-test style. Loads automatically when Kotlin files are touched.
paths: ["**/*.kt", "**/*.kts"]
---

# Kotlin conventions for Mockarr

## detekt tripwires (build fails AT threshold — leave headroom)
- `TooManyFunctions`: 25 max per class. Fix by moving private helpers to file
  level (see `MapViewModel.kt`'s file-level `friendlyMessage`/`quickLastKnown`),
  not by suppressing.
- `CyclomaticComplexMethod`: 15 max. Split big mappers like
  `SettingsRepository.toSettings` + `withFlags`.
- Wrapping: no newline immediately after `->` in a multiline lambda — extract
  the body into a `val` first.
- `SpacingBetweenDeclarationsWithComments`: blank line before a commented
  declaration.
- `LongParameterList` is exempted for `@Composable`/`@HiltViewModel`/`@Inject`;
  everything else keeps ≤6 params.
- Import order (auto-fixed by the format hook): lexicographic, then `java`/
  `javax`/`kotlin`, aliases last.

## Compose pitfalls already paid for once
- Values read only inside `SideEffect`/gesture callbacks are NOT snapshot-
  observed — animations driven that way run once and freeze. Drive map-style
  animations with `Animatable.animateTo` frame callbacks in a `LaunchedEffect`,
  writing layer properties from the callback (no recomposition in the loop).
- Click handlers must read `.value` from StateFlows at click time; capturing
  collected state in the closure goes stale across recompositions (see
  `playOrAskStart` in `MapScreen.kt`).
- Kotlin infers `Nothing?` for empty `suspendCancellableCoroutine { }` — give
  the explicit type parameter.

## Unit tests (`core:*` modules, plus `app`'s pure file-level helpers)
- Test the logic layer: engines, geometry, mappers, ViewModels. No Robolectric
  or Compose UI tests unless explicitly requested.
- Mock only boundaries (repositories, network clients); never mock the unit's
  own logic or pure functions.
- Deterministic always: inject `Random` seeds (see `RouteGeometry`'s `random`
  param) and fixed times; no wall-clock reads in tests.
- Name by behavior: `` fun `holds destination when stayAtDestination enabled`() ``
  style, given–when–then bodies.
- Cover the edge that motivated the change, not just the happy path — and keep
  simulation defaults (e.g. `speedVariance = 0.0`) such that existing tests
  stay byte-identical unless the change is about them.
- `app`-module tests need an explicit `testImplementation(libs.kotlin.test.junit)`:
  AGP's built-in Kotlin does NOT apply the `kotlin-test` → `kotlin-test-junit`
  variant substitution the `org.jetbrains.kotlin.jvm` core modules get, so
  `kotlin.test.Test` compiles but zero tests run without the binding.
