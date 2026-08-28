#!/usr/bin/env bash
# Drive the mockarr_test emulator like a human: boot, tap, swipe, type, look.
# Usage: scripts/emu.sh <command> [args]
set -euo pipefail

SDK="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
ADB="$SDK/platform-tools/adb"
AVD="mockarr_test"

case "${1:-help}" in
  # One emulator per machine. Reuses a running one, recovers an offline one,
  # boots a fresh one only when none exists; every wait has a deadline so a
  # chained command can never hang forever.
  boot)
    state=$("$ADB" devices | awk '/^emulator-/ { print $2; exit }')
    case "$state" in
      device) echo "already booted"; exit 0 ;;
      offline|unauthorized)
        echo "device $state — restarting adb" >&2
        "$ADB" kill-server; "$ADB" start-server; "$ADB" reconnect offline >/dev/null 2>&1 || true
        sleep 3
        if [ "$("$ADB" devices | awk '/^emulator-/ { print $2; exit }')" = "device" ]; then
          echo "recovered"; exit 0
        fi
        echo "still $state — kill it (scripts/emu.sh kill) and boot again" >&2; exit 1 ;;
    esac
    "$SDK/emulator/emulator" -avd "$AVD" -no-window -no-audio -no-boot-anim -no-snapshot >/dev/null 2>&1 &
    deadline=$(( $(date +%s) + ${BOOT_TIMEOUT_S:-180} ))
    until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
      if [ "$(date +%s)" -ge "$deadline" ]; then echo "TIMEOUT: emulator did not boot" >&2; exit 1; fi
      sleep 2
    done
    "$ADB" shell input keyevent KEYCODE_WAKEUP
    "$ADB" shell wm dismiss-keyguard 2>/dev/null || true
    echo "booted"
    ;;
  # Raw adb shell passthrough for one-offs (dumpsys location, settings, …).
  shell)     shift; "$ADB" shell "$@" ;;
  # Wait until the app has drawn and the accessibility tree is readable — the
  # first tap after a launch is otherwise swallowed while the map warms up.
  settle)
    deadline=$(( $(date +%s) + ${2:-20} ))
    while [ "$(date +%s)" -lt "$deadline" ]; do
      if "$ADB" shell "uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 && grep -q 'dev.mockarr.app' /sdcard/ui.xml"; then
        sleep 1.5   # one more frame: MapLibre's first tiles + Compose's first layout
        echo "settled"; exit 0
      fi
      sleep 1
    done
    echo "TIMEOUT: app never settled" >&2; exit 1
    ;;
  # Switch bottom-nav tab by its exact label/desc: scripts/emu.sh tab Routes
  tab)
    coords=$("$0" find "$2")   # find prefers an exact label/desc match
    if [ -z "$coords" ]; then echo "no tab named $2" >&2; exit 1; fi
    # shellcheck disable=SC2086
    "$ADB" shell input tap $coords && echo "switched to $2"
    ;;
  kill)      "$ADB" emu kill ;;
  tap)       "$ADB" shell input tap "$2" "$3" ;;                    # tap X Y
  swipe)     "$ADB" shell input swipe "$2" "$3" "$4" "$5" "${6:-300}" ;;  # swipe X1 Y1 X2 Y2 [ms]
  type)      "$ADB" shell input text "$(printf '%s' "$2" | sed 's/ /%s/g')" ;;  # type "some text"
  key)       "$ADB" shell input keyevent "$2" ;;                    # key 4=back 3=home 66=enter
  home)      "$ADB" shell input keyevent KEYCODE_HOME ;;
  back)      "$ADB" shell input keyevent KEYCODE_BACK ;;
  drawer)    "$ADB" shell input swipe 540 2200 540 600 400 ;;       # open app drawer
  shot)      "$ADB" shell screencap -p /sdcard/shot.png && "$ADB" pull -q /sdcard/shot.png "${2:-shot.png}" && echo "saved ${2:-shot.png}" ;;
  ui)        "$ADB" shell "uiautomator dump /sdcard/ui.xml >/dev/null && cat /sdcard/ui.xml" ;;
  # Print center of the first UI node whose text/desc contains $2 (case-insensitive;
  # supports alternation: find "No saved routes|Search saved")
  find)
    dump=$("$ADB" shell "uiautomator dump /sdcard/ui.xml >/dev/null && cat /sdcard/ui.xml" | tr '>' '\n')
    # Exact attribute match first (text="Play"), substring second — so "Play"
    # hits the button before hint copy that merely contains the word.
    node=$(printf '%s\n' "$dump" | grep -iE "text=\"($2)\"|content-desc=\"($2)\"" | head -1)
    [ -n "$node" ] || node=$(printf '%s\n' "$dump" | grep -iE "text=\"[^\"]*($2)|content-desc=\"[^\"]*($2)" | head -1)
    printf '%s\n' "$node" \
      | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' \
      | head -1 \
      | sed 's/bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]"/\1 \2 \3 \4/' \
      | awk '{ print int(($1+$3)/2), int(($2+$4)/2) }'
    ;;
  # Print the text/content-desc attribute that `find` would match for $2 — makes
  # waitfor/assert self-verifying against substring false positives.
  matchtext)
    "$ADB" shell "uiautomator dump /sdcard/ui.xml >/dev/null && cat /sdcard/ui.xml" \
      | tr '>' '\n' \
      | grep -ioE "text=\"[^\"]*($2)[^\"]*\"|content-desc=\"[^\"]*($2)[^\"]*\"" \
      | head -1
    ;;
  # Poll until UI text/desc appears (default 15s): scripts/emu.sh waitfor "Route from" [timeout_s]
  # Prints the element center on success; exits 1 on timeout. Kills the dialog-timing guesswork.
  waitfor)
    deadline=$(( $(date +%s) + ${3:-15} ))
    while [ "$(date +%s)" -lt "$deadline" ]; do
      coords=$("$0" find "$2")
      if [ -n "$coords" ]; then echo "$coords matched $("$0" matchtext "$2")"; exit 0; fi
      sleep 1
    done
    echo "TIMEOUT waiting for: $2"
    exit 1
    ;;
  # Assert UI text/desc is present right now: scripts/emu.sh assert "Holding at"
  assert)
    coords=$("$0" find "$2")
    if [ -n "$coords" ]; then
      echo "present at $coords: $("$0" matchtext "$2")"
    else
      echo "MISSING: '$2'"
      exit 1
    fi
    ;;
  # Tap the first UI element matching text/desc: scripts/emu.sh tapon "Mock here"
  tapon)
    coords=$("$0" find "$2")
    if [ -z "$coords" ]; then echo "not found: $2" >&2; exit 1; fi
    echo "tapping '$2' at $coords"
    # shellcheck disable=SC2086
    "$ADB" shell input tap $coords
    ;;
  # Recent app log lines, optionally grep-filtered: scripts/emu.sh logcat [pattern] [lines=100]
  logcat)
    if [ -n "${2:-}" ]; then
      "$ADB" logcat -d -t "${3:-100}" | grep -iE "$2" || echo "no logcat lines match: $2"
    else
      "$ADB" logcat -d -t "${3:-100}"
    fi
    ;;
  # Force orientation: scripts/emu.sh rotate landscape|portrait
  rotate)
    case "${2:-}" in
      landscape) target=1 ;;
      portrait)  target=0 ;;
      *) echo "usage: scripts/emu.sh rotate landscape|portrait" >&2; exit 1 ;;
    esac
    "$ADB" shell settings put system accelerometer_rotation 0
    "$ADB" shell settings put system user_rotation "$target"
    echo "rotated to ${2}"
    ;;
  # Emulate other window sizes (e.g. tablet) without a second AVD:
  # scripts/emu.sh resize 2560x1600 320   |   scripts/emu.sh resize reset
  resize)
    if [ "${2:-}" = "reset" ]; then
      "$ADB" shell wm size reset
      "$ADB" shell wm density reset
      echo "display reset"
    else
      "$ADB" shell wm size "$2"
      [ -n "${3:-}" ] && "$ADB" shell wm density "$3"
      echo "display set to $2${3:+ @ ${3}dpi}"
    fi
    ;;
  # System dark mode: scripts/emu.sh night on|off (dark-theme verification passes)
  night)
    case "${2:-}" in
      on)  "$ADB" shell cmd uimode night yes ;;
      off) "$ADB" shell cmd uimode night no ;;
      *) echo "usage: scripts/emu.sh night on|off" >&2; exit 1 ;;
    esac
    ;;
  # Refuses to queue behind a running build (that is the classic silent hang);
  # bounded by INSTALL_TIMEOUT_S so a wedged daemon can't stall a chain either.
  install)
    root="$(cd "$(dirname "$0")/.." && pwd)"
    if find "$root/.gradle" -name '*.lock' -newer "$root/gradlew" -mmin -30 2>/dev/null | grep -q . \
       && pgrep -f 'GradleDaemon' >/dev/null && lsof -t "$root"/.gradle/*/fileHashes/fileHashes.lock >/dev/null 2>&1; then
      echo "Gradle busy (another build holds the lock) — retry when it finishes" >&2; exit 75
    fi
    if [ "$("$ADB" devices | awk '/^emulator-/ { print $2; exit }')" != "device" ]; then
      echo "no online emulator — run scripts/emu.sh boot first" >&2; exit 1
    fi
    ( cd "$root" && perl -e 'alarm shift; exec @ARGV' "${INSTALL_TIMEOUT_S:-300}" scripts/gradle -q :app:installDebug ) \
      || { echo "install failed or timed out" >&2; exit 1; }
    ;;
  launch)    "$ADB" shell am start -n dev.mockarr.app/.MainActivity ;;
  mockallow) "$ADB" shell settings put global development_settings_enabled 1 && "$ADB" shell appops set dev.mockarr.app android:mock_location allow ;;
  mockdeny)  "$ADB" shell appops set dev.mockarr.app android:mock_location deny ;;
  *)
    cat <<'USAGE'
usage: scripts/emu.sh <command> [args]
  boot                       boot mockarr_test (reuses a running one; 180 s deadline)
  settle [timeout=20]        wait until the app has drawn (do this before the first tap)
  tab Map|Routes|Settings    switch bottom-nav tab by content-desc
  shell <adb shell args…>    raw adb shell passthrough
  kill                       shut the emulator down
  launch                     start the Mockarr main activity
  install                    :app:installDebug via scripts/gradle (refuses while Gradle is busy)
  mockallow | mockdeny       grant/revoke the mock-location appop
  tap X Y | swipe X1 Y1 X2 Y2 [ms] | key CODE | home | back | drawer
  type "text"                type into the focused field (spaces ok)
  shot [path.png]            screenshot (default ./shot.png)
  ui                         dump the uiautomator hierarchy XML
  find "text"                center X Y of first node matching text/desc ("a|b" alternation ok)
  matchtext "text"           the attribute find would match (self-verification)
  tapon "text"               find + tap
  waitfor "text" [timeout=15]  poll until present; prints coords + match; exit 1 on timeout
  assert "text"              exit 1 unless present right now
  logcat [pattern] [lines]   recent log lines, optionally filtered (default 100)
  rotate landscape|portrait  force orientation (disables auto-rotate)
  resize WxH [dpi] | reset   emulate another display (tablet testing)
  night on|off               system dark mode (theme verification)
USAGE
    ;;
esac
