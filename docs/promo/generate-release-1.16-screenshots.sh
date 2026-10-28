#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
target=app/androidApp/src/test/kotlin/dev/miyado/shogisupplement/ui/Release116PromoScreenshotTest.kt
if [[ -e "$target" ]]; then
  echo "Temporary screenshot source already exists: $target" >&2
  exit 1
fi
trap 'rm -f "$target"' EXIT
cp docs/promo/Release116PromoScreenshotTest.kt "$target"
(cd app && ./gradlew :androidApp:recordRoborazziDebug --tests '*Release116PromoScreenshotTest*')
python3 docs/promo/render-release-1.16.py
