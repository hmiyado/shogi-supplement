#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
target=app/androidApp/src/test/kotlin/dev/miyado/shogisupplement/ui/Release115PromoScreenshotTest.kt
if [[ -e "$target" ]]; then
  echo "Temporary screenshot source already exists: $target" >&2
  exit 1
fi
trap 'rm -f "$target"' EXIT
cp docs/promo/Release115PromoScreenshotTest.kt "$target"
(cd app && ./gradlew :androidApp:recordRoborazziDebug --tests '*Release115PromoScreenshotTest*')
magick docs/promo/release-1.15-repertoire-full.png -crop 600x960+0+0 +repage docs/promo/release-1.15-repertoire.png
magick docs/promo/release-1.15-labels-full.png -crop 600x340+0+0 +repage docs/promo/release-1.15-labels.png
python3 docs/promo/render-release-1.15.py
