#!/usr/bin/env bash
# Drive the mockarr_test emulator like a human: boot, tap, swipe, type, look.
# Usage: scripts/emu.sh <command> [args]
set -euo pipefail

SDK="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
ADB="$SDK/platform-tools/adb"
AVD="mockarr_test"

case "${1:-help}" in
  boot)
    "$SDK/emulator/emulator" -avd "$AVD" -no-window -no-audio -no-boot-anim -no-snapshot &
    "$ADB" wait-for-device
    until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
      sleep 2
    done
    "$ADB" shell input keyevent KEYCODE_WAKEUP
    "$ADB" shell wm dismiss-keyguard 2>/dev/null || true
    echo "booted"
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
  # Print center of the first UI node whose text/desc contains $2 (case-insensitive)
  find)
    "$ADB" shell "uiautomator dump /sdcard/ui.xml >/dev/null && cat /sdcard/ui.xml" \
      | tr '>' '\n' \
      | grep -i "text=\"[^\"]*$2\|content-desc=\"[^\"]*$2" \
      | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' \
      | head -1 \
      | sed 's/bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]"/\1 \2 \3 \4/' \
      | awk '{ print int(($1+$3)/2), int(($2+$4)/2) }'
    ;;
  # Tap the first UI element matching text/desc: scripts/emu.sh tapon "Mock here"
  tapon)
    coords=$("$0" find "$2")
    if [ -z "$coords" ]; then echo "not found: $2" >&2; exit 1; fi
    echo "tapping '$2' at $coords"
    # shellcheck disable=SC2086
    "$ADB" shell input tap $coords
    ;;
  install)   (cd "$(dirname "$0")/.." && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew -q :app:installDebug) ;;
  mockallow) "$ADB" shell settings put global development_settings_enabled 1 && "$ADB" shell appops set dev.mockarr.app android:mock_location allow ;;
  mockdeny)  "$ADB" shell appops set dev.mockarr.app android:mock_location deny ;;
  *)
    grep -E '^  [a-z]+\)' "$0" | sed 's/).*//' | tr -d ' '
    ;;
esac
