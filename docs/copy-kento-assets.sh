#!/bin/bash
# Why not コミットする: WASM本体(simd/nosimd各1MB弱)自体は小さいが、評価関数nn.bin
# (数十MB)を含めるとdocs/配下がリポジトリの主要サイズを占めてしまう。GH Pagesは
# リポジトリのコミット履歴をそのまま配信する仕組みではない(ビルド成果物の配置)ため、
# コミット不要でも配信は成立する。docs/kento-assets/は.gitignore対象。
#
# バージョン付きサブディレクトリへ配置する理由: ページ側はWASMバイナリの参照先を
# 「ベースURL(1箇所の設定)＋/<エンジンバージョン>/」で組み立てる想定になっている。
# ここでもengine-wasm/VERSIONの値をそのままサブディレクトリ名に使うことで、
# ページ側の実行時判断と本スクリプトの配置が食い違わないようにする。将来WASMバイナリを
# CloudFront+S3等の外部ホストへ移す場合も同じ構造(ベースURL＋バージョン付きパス)を
# 使う想定。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SRC_OUT_BROWSER="$REPO_ROOT/engine-wasm/out-browser"
DEST="$SCRIPT_DIR/kento-assets"
APP_DIR="$REPO_ROOT/app"

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

# :webApp (CMP for Web・レポート画面本体) のwasmJs本番ビルド。エンジンWASMバイナリと違って
# 毎回このリポジトリのソースから再現できる(上流フェッチもDockerも不要)ため、
# このスクリプト自身がGradleビルドまで完結させる。バージョン付きディレクトリに
# 分けない理由: 外部からfetchするWASMバイナリではなく、このリポジトリのコミットと
# 常に同じ内容になる(バージョンという概念がそもそも無い)。
WEBAPP_DIST="$APP_DIR/webApp/build/dist/wasmJs/productionExecutable"
echo ":webApp (CMP for Web) をビルドします..."
( cd "$APP_DIR" && ./gradlew :webApp:wasmJsBrowserDistribution --console=plain )

if [ ! -f "$WEBAPP_DIST/webApp.js" ]; then
  echo "エラー: webApp.js のビルド成果物が見つかりません ($WEBAPP_DIST)" >&2
  exit 1
fi

KENTO_DIR="$SCRIPT_DIR/kento"
cp "$WEBAPP_DIST"/webApp.js "$WEBAPP_DIST"/*.wasm "$KENTO_DIR"/
[ -f "$WEBAPP_DIST/webApp.js.map" ] && cp "$WEBAPP_DIST/webApp.js.map" "$KENTO_DIR"/

# composeResources(フォント等)はdocs/kento.html自身から見た相対パス("./composeResources/...")
# で解決される(webApp.js等と違いスクリプト自身の場所基準ではない)ため、
# ページと同じdocs/直下に置く。
rm -rf "$SCRIPT_DIR/composeResources"
cp -r "$WEBAPP_DIST/composeResources" "$SCRIPT_DIR/composeResources"

echo "コピー完了: $KENTO_DIR/webApp.js"
du -sh "$KENTO_DIR/webApp.js"

# 悪手判定の係数表。:webAppはDBを持たないため、Android同梱の正本を
# ビルド時にコピーしてfetchで読む(複数箇所に正本を分散させないため)。
cp "$REPO_ROOT/app/androidApp/src/main/assets/coefficients_hao_isolate_v1.json" "$KENTO_DIR"/
echo "コピー完了: $KENTO_DIR/coefficients_hao_isolate_v1.json"

# 完全性検証用のマニフェスト。全ファイルを配置し終えた後に採る（途中で採ると
# 未配置のファイルが載らない）。
"$SCRIPT_DIR/generate-kento-manifest.sh" "$SCRIPT_DIR" "$DEST/MANIFEST.json"
