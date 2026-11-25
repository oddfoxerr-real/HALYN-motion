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
test "$(find screenshots -name '0*.png' | wc -l)" -eq 7

# Exercise the real system picker from a normal activity, not within instrumentation.
adb shell am force-stop "$app"
adb shell am start -W -n "$app/.Editor"
adb shell input keyevent 82
sleep 2
tap_label() {
    local label="$1"
    adb shell uiautomator dump /sdcard/strata-picker.xml >/dev/null 2>&1 || return 1
    adb pull /sdcard/strata-picker.xml /tmp/strata-picker.xml >/dev/null 2>&1 || return 1
    local xy
    xy=$(python3 - "$label" <<'PY'
import sys,re,xml.etree.ElementTree as ET
label=sys.argv[1].casefold()
for n in ET.parse('/tmp/strata-picker.xml').iter('node'):
    if label in (n.get('text','').casefold(),n.get('content-desc','').casefold()):
        a,b,c,d=map(int,re.findall(r'\d+',n.get('bounds','')))
        print((a+c)//2,(b+d)//2)
        break
else: sys.exit(1)
PY
) || return 1
    adb shell input tap $xy
    sleep 1
}
tap_label "Image Settings"
tap_label "Preview"
tap_label "Apply"
# Wait for Android activity transition and check actual resumed component.
picker=""
for i in $(seq 1 20); do
    adb shell dumpsys activity activities > screenshots/activity.txt
    picker=$(grep -E 'topResumedActivity|mResumedActivity' screenshots/activity.txt | tail -1 || true)
    if [[ -n "$picker" && "$picker" != *"$app/.Editor"* ]]; then break; fi
    sleep 1
done
printf '%s\n' "$picker" | tee screenshots/picker-result.txt
adb logcat -d -s STRATA_APPLY AndroidRuntime > screenshots/apply-log.txt
if [[ -z "$picker" || "$picker" == *"$app/.Editor"* ]]; then
    adb exec-out screencap -p > screenshots/picker-failure.png
    cat screenshots/apply-log.txt
    adb shell cmd package resolve-activity --brief -a android.service.wallpaper.CHANGE_LIVE_WALLPAPER || true
    exit 1
fi
adb exec-out screencap -p > screenshots/08-android-wallpaper-picker.png
# Actual system confirmation, if exposed by this Android image.
for i in $(seq 1 4); do
    adb shell dumpsys wallpaper > screenshots/wallpaper-state.txt
    if grep -q 'studio.strata.wallpaper/.LiveWallpaper' screenshots/wallpaper-state.txt; then break; fi
    tap_label "Strata Live Wallpaper" || tap_label "Strata" || tap_label "Set wallpaper" || tap_label "Apply" || tap_label "Home screen" || tap_label "Home screen and lock screen" || true
    sleep 2
done
adb shell dumpsys wallpaper > screenshots/wallpaper-state.txt
if grep -E 'mWallpaperComponent=|wallpaperComponent=' screenshots/wallpaper-state.txt | grep -q 'studio.strata.wallpaper'; then
    echo "STRATA_WALLPAPER_APPLIED: Android reports STRATA as active wallpaper" | tee -a screenshots/instrumentation.log
else
    echo "STRATA_PICKER_OPENED: system picker launched; final selection not confirmed" | tee -a screenshots/instrumentation.log
fi
adb exec-out screencap -p > screenshots/09-wallpaper-state.png

