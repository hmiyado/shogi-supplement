#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SOURCE_ROOT="${1:?usage: build_android.sh <YaneuraOu checkout> <NDK directory> <output directory>}"
NDK_DIR="${2:?missing NDK directory}"
OUT_DIR="${3:?missing output directory}"
EXPECTED_COMMIT=717da871e7a620702b8b9433bd8f9f181710435a
if [ "$(git -C "$SOURCE_ROOT" rev-parse HEAD)" != "$EXPECTED_COMMIT" ] ||
   [ -n "$(git -C "$SOURCE_ROOT" status --porcelain --untracked-files=all)" ]; then
    echo "Expected an unmodified YaneuraOu checkout at $EXPECTED_COMMIT" >&2
    exit 1
fi
mkdir -p "$OUT_DIR/obj"
OUT_DIR="$(cd "$OUT_DIR" && pwd)"
python3 "$SCRIPT_DIR/../iosApp/engine/prepare_source.py" "$SOURCE_ROOT/source" "$OUT_DIR/source"
CXX="$NDK_DIR/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android26-clang++"
FLAGS=(-std=c++17 -fno-exceptions -fno-rtti -O3 -DNDEBUG -fPIE
    -D_LINUX -DUSE_MAKEFILE -DYANEURAOU_ENGINE_NNUE
    -DENGINE_NAME_FROM_MAKEFILE=YaneuraOu_NNUE -DIS_64BIT -DUSE_NEON
    -Wno-unused-parameter -Wno-unused-command-line-argument)
OBJECTS=()
while IFS= read -r source; do
    [ -n "$source" ] || continue
    object="$OUT_DIR/obj/${source//\//__}.o"
    OBJECTS+=("$object")
    echo "CC $source"
    "$CXX" "${FLAGS[@]}" -c "$OUT_DIR/source/$source" -o "$object"
done < "$SCRIPT_DIR/yaneuraou-v940-sources.txt"
"$CXX" -static-libstdc++ -fPIE -pie -Wl,-z,max-page-size=16384 \
    "${OBJECTS[@]}" -o "$OUT_DIR/libyaneuraou_usi.so"
"$NDK_DIR/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-strip" "$OUT_DIR/libyaneuraou_usi.so"
shasum -a 256 "$OUT_DIR/libyaneuraou_usi.so"
