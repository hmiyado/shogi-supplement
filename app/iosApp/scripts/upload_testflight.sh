#!/bin/sh
# TestFlightへのアーカイブ＆アップロード（エンジン非同梱スキーム）。
set -eu
cd "$(dirname "$0")/.."

KEY_ID="BM62Q97564"
ISSUER_ID="9f6a60f5-d95c-484a-9c8c-574f65a93b7b"
# ビルド番号はUTC時刻で一意化（同一分内の再実行は不可＝実用上問題ない粒度）
BUILD_NUMBER=$(date -u +%y%m%d%H%M)
ARCHIVE="build/testflight/shogisup.xcarchive"

xcodegen generate

xcodebuild -project iosApp.xcodeproj -scheme iosApp-Engineless -configuration Release-Engineless \
  archive -archivePath "$ARCHIVE" \
  CURRENT_PROJECT_VERSION="$BUILD_NUMBER" \
  DEVELOPMENT_TEAM=C8DX3743C6 CODE_SIGN_STYLE=Automatic \
  -allowProvisioningUpdates \
  -authenticationKeyID "$KEY_ID" \
  -authenticationKeyIssuerID "$ISSUER_ID"

xcodebuild -exportArchive -archivePath "$ARCHIVE" \
  -exportOptionsPlist scripts/ExportOptions-testflight.plist \
  -allowProvisioningUpdates \
  -authenticationKeyID "$KEY_ID" \
  -authenticationKeyIssuerID "$ISSUER_ID"

echo "TestFlightへアップロード完了 (build=$BUILD_NUMBER)"
