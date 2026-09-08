# Contributing to Mockarr

Mockarr is source-available under the [PolyForm Strict License 1.0.0](LICENSE): you can read,
build and run it for noncommercial purposes, but the licence does not permit modified or derived
works, so **pull requests are not accepted**. Issues are welcome, especially device-specific bug
reports — mock location behaviour varies by manufacturer. The reasoning is in
[ADR 0004](docs/adr/0004-source-available-licence.md).

## Filing a good bug report

Always include the device model, Android version and Play services version; the issue template
asks for them. Screenshots or a screen recording of the status line and the notification help
more than logs.

## Building it yourself (noncommercial use)

1. JDK 17+ and the Android SDK (Android Studio's bundled versions work).
2. `./gradlew build` runs unit tests, Android lint and detekt.
3. No API keys or accounts are needed. With a `GEOAPIFY_KEY` in a git-ignored
   `secrets.properties` the app uses Geoapify and unlocks walking and cycling; without one it uses
   the public OSRM and Photon servers (driving only).

## Ground rules that shape the code

- **Style**: detekt (with ktlint formatting rules) is the single source of truth.
- **Architecture**: `:core:model`, `:core:simulation` and `:core:routing` are pure Kotlin and must
  never import Android; a Gradle task (`checkCoreBoundary`) fails the build if they do. Hilt
  bindings live in `:app/di`; backends implement `RouteProvider`, `Geocoder`, `ElevationProvider`.
- **Tests**: the simulation engine and routing layer are unit-tested under virtual time with
  seeded randomness.
- **Scope**: Mockarr contains no anti-detection features (hiding mock status from other apps) and
  never will.

## Testing without a device

An emulator works well. Useful commands:

```sh
adb shell appops set dev.mockarr.app android:mock_location allow   # select as mock app
adb shell appops set dev.mockarr.app android:mock_location deny    # simulate not-selected
adb shell dumpsys location | grep -E "provider \[mock\]"           # verify mock providers
```

Note: on emulator (userdebug) images the *default* appop mode already behaves as allowed.
