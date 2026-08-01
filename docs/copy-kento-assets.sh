#!/bin/bash
# Why not コミットする: WASM本体(simd/nosimd各1MB弱)自体は小さいが、評価関数nn.bin
# (数十MB)を含めるとdocs/配下がリポジトリの主要サイズを占めてしまう。GH Pagesは
# リポジトリのコミット履歴をそのまま配信する仕組みではない(ビルド成果物の配置)ため、
# コミット不要でも配信は成立する。docs/kento-assets/は.gitignore対象。
#
# バージョン付きサブディレクトリへ配置する理由: ページ側は資産の参照先を
# 「ベースURL(1箇所の設定)＋/<エンジンバージョン>/」で組み立てる想定になっている。
# ここでもengine-wasm/VERSIONの値をそのままサブディレクトリ名に使うことで、
# ページ側の実行時判断と本スクリプトの配置が食い違わないようにする。将来資産を
# CloudFront+S3等の外部ホストへ移す場合も同じ構造(ベースURL＋バージョン付きパス)を
# 使う想定。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SRC_OUT_BROWSER="$REPO_ROOT/engine-wasm/out-browser"
DEST="$SCRIPT_DIR/kento-assets"
APP_DIR="$REPO_ROOT/app"
KIFU_JS_OUT="$APP_DIR/kifu/build/kotlin-webpack/js/productionExecutable"

if [ ! -f "$SRC_OUT_BROWSER/yaneuraou-simd.wasm" ] || [ ! -f "$SRC_OUT_BROWSER/nn.bin" ] || [ ! -f "$SRC_OUT_BROWSER/VERSION" ]; then
  echo "エラー: engine-wasm/ のビルド成果物が見つかりません。" >&2
  echo "  engine-wasm/ で ./fetch_upstream.sh && ./build_wasm_browser.sh を実行してから再実行してください。" >&2
  echo "  (engine-wasm/out-browser/ を参照します)" >&2
  exit 1
fi

VERSION="$(tr -d '[:space:]' < "$SRC_OUT_BROWSER/VERSION")"
if [ -z "$VERSION" ]; then
  echo "エラー: engine-wasm/out-browser/VERSION が空です。" >&2
  exit 1
fi

DEST_VERSIONED="$DEST/$VERSION"
mkdir -p "$DEST_VERSIONED"
cp "$SRC_OUT_BROWSER"/yaneuraou-simd.js "$SRC_OUT_BROWSER"/yaneuraou-simd.wasm "$DEST_VERSIONED"/
cp "$SRC_OUT_BROWSER"/yaneuraou-nosimd.js "$SRC_OUT_BROWSER"/yaneuraou-nosimd.wasm "$DEST_VERSIONED"/
cp "$SRC_OUT_BROWSER"/nn.bin "$DEST_VERSIONED"/
# ページ側が「今どのバージョンを使うか」を実行時に判断するためのマーカー。
# ベースディレクトリ直下に置く(バージョン付きサブディレクトリの中ではない)。
cp "$SRC_OUT_BROWSER"/VERSION "$DEST"/VERSION

echo "コピー完了: $DEST_VERSIONED (バージョン: $VERSION)"
du -sh "$DEST_VERSIONED"

# KIFパーサ(Kotlin/JS)バンドル。エンジン資産と違って毎回このリポジトリのソースから
# 再現できる(上流フェッチもDockerも不要)ため、このスクリプト自身がGradleビルドまで
# 完結させる(ローカル確認がこのスクリプト1本で済むようにするため)。Gradleは
# 増分ビルドなので再実行のコストは小さい。バージョン付きディレクトリに分けない理由:
# WASMエンジンと違って外部からfetchする資産ではなく、このリポジトリのコミットと
# 常に同じ内容になる(バージョンという概念がそもそも無い)。
echo "kifu.js (KIFパーサ Kotlin/JS バンドル) をビルドします..."
( cd "$APP_DIR" && ./gradlew :kifu:jsBrowserProductionWebpack --console=plain )

if [ ! -f "$KIFU_JS_OUT/kifu.js" ]; then
  echo "エラー: kifu.js のビルド成果物が見つかりません ($KIFU_JS_OUT)" >&2
  exit 1
fi

cp "$KIFU_JS_OUT/kifu.js" "$DEST/kifu.js"
[ -f "$KIFU_JS_OUT/kifu.js.map" ] && cp "$KIFU_JS_OUT/kifu.js.map" "$DEST/kifu.js.map"

echo "コピー完了: $DEST/kifu.js"
du -sh "$DEST/kifu.js"
