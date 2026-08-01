#!/bin/bash
# Why not コミットする: WASM本体(simd/nosimd各1MB弱)自体は小さいが、評価関数nn.bin
# (数十MB)を含めるとdocs/配下がリポジトリの主要サイズを占めてしまう。GH Pagesは
# リポジトリのコミット履歴をそのまま配信する仕組みではない(ビルド成果物の配置)ため、
# コミット不要でも配信は成立する。docs/kento-assets/は.gitignore対象。
#
# 前提: engine-wasm/ でブラウザ向けビルドが完了していること
#   (engine-wasm/fetch_upstream.sh → engine-wasm/build_wasm_browser.sh)。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SRC_OUT_BROWSER="$REPO_ROOT/engine-wasm/out-browser"
DEST="$SCRIPT_DIR/kento-assets"

if [ ! -f "$SRC_OUT_BROWSER/yaneuraou-simd.wasm" ] || [ ! -f "$SRC_OUT_BROWSER/nn.bin" ]; then
  echo "エラー: engine-wasm/ のビルド成果物が見つかりません。" >&2
  echo "  engine-wasm/ で ./fetch_upstream.sh && ./build_wasm_browser.sh を実行してから再実行してください。" >&2
  echo "  (engine-wasm/out-browser/ を参照します)" >&2
  exit 1
fi

mkdir -p "$DEST"
cp "$SRC_OUT_BROWSER"/yaneuraou-simd.js "$SRC_OUT_BROWSER"/yaneuraou-simd.wasm "$DEST"/
cp "$SRC_OUT_BROWSER"/yaneuraou-nosimd.js "$SRC_OUT_BROWSER"/yaneuraou-nosimd.wasm "$DEST"/
cp "$SRC_OUT_BROWSER"/nn.bin "$DEST"/

echo "コピー完了: $DEST"
du -sh "$DEST"
