#!/usr/bin/env bash
set -euo pipefail
mkdir -p screenshots
adb shell wm size 1080x2340
adb shell wm density 420
adb install -r dist/Strata-0.3.0-debug.apk
app=studio.strata.wallpaper
adb shell am start -W -n "$app/.Editor"
sleep 3
adb shell pidof "$app"
adb exec-out screencap -p > screenshots/01-background.png
adb shell am force-stop "$app"
python3 - <<'PY'
import json, struct, zlib
from pathlib import Path
w,h=540,1170
raw=bytearray()
for y in range(h):
    raw.append(0)
    for x in range(w):
        v=int(15+20*y/h)
        if (x-w*.5)**2+(y-h*.32)**2 < 105**2: v+=34
        raw.extend((v,v+3,v+6))
def chunk(t,d):return struct.pack(">I",len(d))+t+d+struct.pack(">I",zlib.crc32(t+d)&0xffffffff)
Path("/tmp/strata-fixture.png").write_bytes(b"\x89PNG\r\n\x1a\n"+chunk(b"IHDR",struct.pack(">IIBBBBB",w,h,8,2,0,0,0))+chunk(b"IDAT",zlib.compress(raw))+chunk(b"IEND",b""))
scene={"background":-16777216,"backgroundSet":True,"duration":6000,"layers":[{"id":"sample-base","name":"Base image","kind":"image","content":"sample.png","depth":0,"depthY":0,"parallaxOn":False,"base":{"x":.5,"y":.5,"scale":1,"rotation":0,"opacity":1},"keys":[]}]}
Path("/tmp/strata-scene.json").write_text(json.dumps(scene))
PY
adb shell "run-as $app mkdir -p files"
adb shell "run-as $app sh -c 'cat > files/sample.png'" < /tmp/strata-fixture.png
adb shell "run-as $app sh -c 'cat > files/scene.json'" < /tmp/strata-scene.json
adb shell am start -W -n "$app/.Editor"
sleep 3
adb shell pidof "$app"
adb exec-out screencap -p > screenshots/02-editor.png
tap_label() {
  adb shell uiautomator dump /sdcard/strata-ui.xml >/dev/null
  adb pull /sdcard/strata-ui.xml /tmp/strata-ui.xml >/dev/null
  local coords
  coords=$(python3 - "$1" <<'PY'
import re,sys,xml.etree.ElementTree as E
for n in E.parse('/tmp/strata-ui.xml').iter('node'):
    if sys.argv[1] in (n.get('text'),n.get('content-desc')):
        a,b,c,d=map(int,re.findall(r'\d+',n.get('bounds')))
        print((a+c)//2,(b+d)//2)
        break
else: raise SystemExit('Missing control: '+sys.argv[1])
PY
)
  adb shell input tap $coords
  sleep 1
}
tap_label "Image Settings"
adb exec-out screencap -p > screenshots/03-motion-settings.png
tap_label "Close settings"
tap_label "Add to layer"
adb exec-out screencap -p > screenshots/04-add-layer.png
adb shell pidof "$app"
