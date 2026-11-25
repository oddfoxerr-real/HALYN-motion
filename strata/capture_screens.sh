#!/usr/bin/env bash
set -euo pipefail
mkdir -p screenshots
adb shell wm size 1080x2340
adb shell wm density 420
adb install -r dist/Strata-0.3.0-debug.apk
adb install -r dist/qa.apk
app=studio.strata.wallpaper
adb shell pm clear "$app"
set +e
adb shell am instrument -w -r "$app.test/studio.strata.wallpaper.StrataQa" | tee screenshots/instrumentation.log
status=$?
set -e
for f in $(adb shell "run-as $app ls files/qa" | tr -d '\r' | grep -E '^[a-zA-Z0-9_-]+\.png$' || true); do
  adb exec-out run-as "$app" cat "files/qa/$f" > "screenshots/$f"
done
adb logcat -d -s STRATA_QA AndroidRuntime > screenshots/logcat.txt
test "$status" -eq 0
grep -q STRATA_QA_PASS screenshots/instrumentation.log
test "$(find screenshots -name '0*.png' | wc -l)" -eq 8
