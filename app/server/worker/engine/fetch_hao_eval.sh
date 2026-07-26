#!/bin/bash
# Háo評価関数（tanuki-, nodchip, GPLv3）をGitHub Releasesから取得し、SHA-256を照合する。
# analysis-worker Dockerfile の eval-fetch ステージから呼ばれる想定
# （curl・7z・sha256sumがあればコンテナ外でも同様に動く:
#   ./fetch_hao_eval.sh <expected_sha256> <out_dir>）。
#
# Why not Secrets経由の配布URLや事前アーティファクト化: nodchip/tanuki-のGitHub Release
# アセットは認証不要で公開取得できるため、追加の配布経路を用意する必要がない。
# 取得後は呼び出し側から渡されたSHA-256（ENGINE_EVAL_SHA256ビルド引数）で照合し、
# 差分があれば即failする。バージョン更新時はURLとハッシュを揃えて差し替える
# （URL自体はリリースに紐づく固定値のためハードコードし、可変なのはハッシュ側のみ）。
set -euo pipefail

EXPECTED_SHA256="${1:?usage: fetch_hao_eval.sh <expected_sha256> <out_dir>}"
OUT_DIR="${2:?usage: fetch_hao_eval.sh <expected_sha256> <out_dir>}"

HAO_URL="https://github.com/nodchip/tanuki-/releases/download/tanuki-.halfkp_256x2-32-32.2023-05-08/tanuki-.halfkp_256x2-32-32.2023-05-08.7z"

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

echo "=== Downloading Háo evaluation function ==="
curl -fsSL -o "$WORK_DIR/hao.7z" "$HAO_URL"

mkdir -p "$WORK_DIR/extract"
7z x -o"$WORK_DIR/extract" "$WORK_DIR/hao.7z" >/dev/null

NN_BIN="$WORK_DIR/extract/eval/nn.bin"
if [ ! -f "$NN_BIN" ]; then
  echo "7z展開後に eval/nn.bin が見つかりません（配布アーカイブの構成が変わった可能性）" >&2
  find "$WORK_DIR/extract" -maxdepth 3 >&2
  exit 1
fi

ACTUAL_SHA256="$(sha256sum "$NN_BIN" | cut -d' ' -f1)"
if [ "$ACTUAL_SHA256" != "$EXPECTED_SHA256" ]; then
  echo "Háo nn.binのSHA-256が想定と不一致" >&2
  echo "  期待値: $EXPECTED_SHA256" >&2
  echo "  実際値: $ACTUAL_SHA256" >&2
  exit 1
fi
echo "SHA-256一致確認OK: $ACTUAL_SHA256"

mkdir -p "$OUT_DIR"
cp "$NN_BIN" "$OUT_DIR/nn.bin"
if [ -f "$WORK_DIR/extract/gpl-3.0.txt" ]; then
  cp "$WORK_DIR/extract/gpl-3.0.txt" "$OUT_DIR/gpl-3.0.txt"
fi

echo "=== Done ==="
ls -lh "$OUT_DIR"
