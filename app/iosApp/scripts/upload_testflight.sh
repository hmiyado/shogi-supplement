#!/bin/sh
# TestFlightへのアーカイブ＆アップロード（エンジン非同梱スキーム）。
set -eu
cd "$(dirname "$0")/.."

KEY_ID="BM62Q97564"
ISSUER_ID="9f6a60f5-d95c-484a-9c8c-574f65a93b7b"
# ビルド番号はgradle.propertiesのshogisupplement.versionCodeと一致させる。
# Why notタイムスタンプ一意化: 強制アップデート判定がCFBundleVersionを
# min_buildと比較するため、判定に使う値と実際のビルド番号が同一源である必要がある。
# 再アップロードにはversionCodeの繰り上げコミットが必要になるが、それが正しい運用
BUILD_NUMBER=$(grep '^shogisupplement.versionCode=' ../gradle.properties | cut -d= -f2)
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
