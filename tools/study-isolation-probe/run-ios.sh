#!/bin/bash
set -euo pipefail
repo=$(cd "$(dirname "$0")/../.." && pwd)
probe="$repo/tools/study-isolation-probe"
device=${1:?Specify an available simulator UDID}
output=${2:-"$repo/build/study-isolation-ios.json"}
mkdir -p "$repo/build"
work=$(mktemp -d "$repo/build/study-isolation.XXXXXX")
app="$work/Probe.app"
mkdir -p "$app"
cp "$probe/probe.html" "$app/probe.html"
python3 - "$app/Info.plist" <<'PY'
import plistlib, sys
with open(sys.argv[1], 'wb') as f:
    plistlib.dump({'CFBundleIdentifier':'dev.miyado.issue102-probe', 'CFBundleName':'Study Isolation Probe',
        'CFBundleExecutable':'Probe', 'CFBundlePackageType':'APPL', 'CFBundleVersion':'1',
        'CFBundleShortVersionString':'1.0', 'MinimumOSVersion':'17.0', 'LSRequiresIPhoneOS':True,
        'UIDeviceFamily':[1,2], 'NSAppTransportSecurity':{'NSAllowsLocalNetworking':True,
        'NSAllowsArbitraryLoadsInWebContent':True}}, f)
PY
xcrun --sdk iphonesimulator swiftc -sdk "$(xcrun --sdk iphonesimulator --show-sdk-path)" \
    -target arm64-apple-ios17.0-simulator "$probe/Probe.swift" -o "$app/Probe"
codesign --force --sign - "$app"
# 対象シミュレータの起動は利用者が行う。製品アプリには触れず、専用アプリだけを上書きする。
xcrun simctl install "$device" "$app"
xcrun simctl launch --terminate-running-process "$device" dev.miyado.issue102-probe
probe_data=$(xcrun simctl get_app_container "$device" dev.miyado.issue102-probe data)
# 前回の5件を拾わないよう、結果ファイルが今回の実行時刻以降になっていることも確認する。
python3 - "$probe_data/Documents/results.json" "$app/Probe" "$output" <<'PY'
import json, pathlib, sys, time
source, executable, output = map(pathlib.Path, sys.argv[1:])
for _ in range(55):
    try:
        data = json.loads(source.read_text())
        if source.stat().st_mtime >= executable.stat().st_mtime and len(data) == 5:
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n')
            print(output)
            break
    except (FileNotFoundError, json.JSONDecodeError):
        pass
    time.sleep(1)
else:
    raise SystemExit('Probe did not finish; inspect simulator and local server')
PY
xcrun simctl terminate "$device" dev.miyado.issue102-probe
