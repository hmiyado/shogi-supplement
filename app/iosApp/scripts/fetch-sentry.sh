#!/bin/sh
# Sentry XCFrameworkのベンダリング取得（バージョン固定）。
# xcodebuild CLIのSPM解決が本環境でハングするため、SPMではなく
# ビルド済みXCFrameworkを vendor/ に置く方式を採る（vendor/はgit管理外）。
set -e
VERSION="8.58.4"
DIR="$(dirname "$0")/../vendor"
mkdir -p "$DIR"
cd "$DIR"
if [ -d "Sentry-Dynamic.xcframework" ]; then
  echo "Sentry-Dynamic.xcframework already exists (delete to re-fetch)"
  exit 0
fi
curl -sL -o sentry.zip "https://github.com/getsentry/sentry-cocoa/releases/download/${VERSION}/Sentry-Dynamic.xcframework.zip"
unzip -q sentry.zip
rm sentry.zip
echo "fetched Sentry-Dynamic.xcframework ${VERSION}"
