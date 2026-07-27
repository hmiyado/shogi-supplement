#!/bin/sh
# Firebase Apple SDK（FirebaseCore・FirebaseAppCheckとその依存）のXCFrameworkを
# ベンダリング取得する（バージョン固定）。fetch-sentry.shと同じ流儀
# （SPMはこの環境のxcodebuild CLIで解決がハングするため、ビルド済み
# XCFrameworkをvendor/へ置く。vendor/はgit管理外）。
#
# Why not fetch-sentry.shと同じ「curl+unzip全体」: Firebaseの配布物は
# 「Firebase.zip」1本（全プロダクト・全プラットフォーム同梱で350MB超）のみで、
# 個別プロダクトごとの軽量zipは提供されていない。素朴に全体をcurlすると
# 使わないFirestore/Analytics/Crashlytics等まで含めて数百MB落とすことになるため、
# 必要なXCFrameworkだけをHTTP Rangeリクエストで個別に取り出す（ZIPの中央
# ディレクトリからオフセットを引き、該当バイト範囲だけを取得する）。
# 併せて iOS実機/シミュレータ以外のプラットフォームスライス
# （tvos/watchos/macos/maccatalyst）もXCFramework内から間引く
# （Info.plistのAvailableLibrariesも対応するエントリだけに絞り直す）。
set -e
DIR="$(cd "$(dirname "$0")/.." && pwd)/vendor"
VERSION="12.16.0"
mkdir -p "$DIR"

if [ -d "$DIR/Firebase/FirebaseCore.xcframework" ]; then
  echo "Firebase XCFrameworks already exist under vendor/Firebase (delete to re-fetch)"
  exit 0
fi

python3 "$(dirname "$0")/fetch_firebase_xcframeworks.py" "$VERSION" "$DIR/Firebase"
echo "fetched Firebase XCFrameworks ${VERSION} (FirebaseCore/FirebaseAppCheckとその依存)"
