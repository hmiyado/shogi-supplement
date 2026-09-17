#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "usage: $0 OUTPUT_DIR" >&2
  exit 64
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
UPSTREAM_DIR="$SCRIPT_DIR/upstream"
SRC="$UPSTREAM_DIR/source"
SOURCE_LIST="$REPO_ROOT/app/engine/yaneuraou-v940-sources.txt"
OUT_DIR="$(mkdir -p "$1" && cd "$1" && pwd)"
OBJ_DIR="$OUT_DIR/obj"
EXPECTED_COMMIT="717da871e7a620702b8b9433bd8f9f181710435a"

actual_commit="$(git -C "$UPSTREAM_DIR" rev-parse HEAD)"
if [ "$actual_commit" != "$EXPECTED_COMMIT" ]; then
  echo "unexpected YaneuraOu commit: $actual_commit" >&2
  exit 1
fi
if [ -n "$(git -C "$UPSTREAM_DIR" status --porcelain --untracked-files=all)" ]; then
  echo "YaneuraOu checkout has local changes" >&2
  exit 1
fi

SDKROOT="$(xcrun --sdk macosx --show-sdk-path)"
mkdir -p "$OBJ_DIR"
objects=()

while IFS= read -r source_file; do
  [ -n "$source_file" ] || continue
  object_file="$OBJ_DIR/${source_file//\//__}.o"
  objects+=("$object_file")
  xcrun --sdk macosx clang++ \
    -std=c++17 -fno-exceptions -fno-rtti -O3 -DNDEBUG -fPIC \
    -isysroot "$SDKROOT" -target arm64-apple-macos11 \
    -DUSE_MAKEFILE -DYANEURAOU_ENGINE_NNUE \
    -DENGINE_NAME_FROM_MAKEFILE=YaneuraOu_NNUE \
    -DTARGET_CPU=\"APPLEM1\" -DIS_64BIT -DUSE_NEON \
    -Wno-unused-parameter -Wno-unused-command-line-argument \
    -c "$SRC/$source_file" -o "$object_file"
done < "$SOURCE_LIST"

xcrun --sdk macosx clang++ \
  -fPIC -target arm64-apple-macos11 -isysroot "$SDKROOT" \
  -o "$OUT_DIR/YaneuraOu-NNUE-m1" "${objects[@]}" -lpthread

file "$OUT_DIR/YaneuraOu-NNUE-m1"
