# Contributing to Mockarr

Thanks for your interest! Mockarr is early and moving fast — small, focused PRs land easiest.

## Setup

1. JDK 17+ and the Android SDK (Android Studio's bundled versions work).
2. `./gradlew build` must pass — it runs unit tests, Android lint, and detekt.
3. No API keys or accounts are needed for anything in this project.

## Ground rules

- **Style**: detekt (with ktlint formatting rules) is the single source of truth — `./gradlew detekt`. Notable conventions the linter enforces: `java`/`javax`/`kotlin` import groups last, no wildcard imports, trailing commas.
- **Architecture**: core modules stay Android-framework-free where marked (`:core:model`, `:core:simulation`, `:core:routing` are pure Kotlin) and Hilt-free (bindings live in `:app/di`). New backends should implement existing interfaces (`RouteProvider`, `MockLocationController`).
- **Tests**: the simulation engine and routing layer are fully unit-tested — keep it that way. Bug fixes in those modules need a regression test.
- **Scope**: Mockarr will not merge anti-detection features (hiding mock status from other apps). This keeps the project's standing clean with F-Droid and app stores.

## Device quirks

Mock location behavior varies by manufacturer. When filing bugs, always include device model, Android version, and Play services version — the issue template asks for them.

## Testing without a device

An emulator works well. Useful commands:

```sh
adb shell appops set dev.mockarr.app android:mock_location allow   # select as mock app
adb shell appops set dev.mockarr.app android:mock_location deny    # simulate not-selected
adb shell dumpsys location | grep -E "provider \[mock\]"           # verify mock providers
```

Note: on emulator (userdebug) images the *default* appop mode already behaves as allowed.

## License note

The project's license is not yet chosen. By contributing before that decision, you agree your contribution may be released under whichever OSI-approved license the project adopts.
