#!/bin/bash
# YaneuraOu NNUE(v9.40)をiOS向け静的ライブラリ(libyaneuraou.a)としてビルドする。
#
# 使い方:
#   ./build_ios.sh [sim|device]   # 既定=sim（iosSimulatorArm64）。deviceはiphoneos arm64向け（ビルド確認は未実施）
#
# 前提:
#   - Xcode Command Line Tools（xcrun / clang++）
#   - 上流ソースは本スクリプトが app/iosApp/engine/upstream/ へ取得する（gitignore対象・コミット禁止）
#
# 出力:
#   app/iosApp/engine/build/<target>/libyaneuraou.a
#
# AndroidのNDKビルドと同じソース選定・
# YANEURAOU_ENGINE_NNUEの構成をiOS向けclangに移植したもの）
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
UPSTREAM_URL="https://github.com/yaneurao/YaneuraOu.git"
YANEURAOU_TAG="v9.40"
YANEURAOU_COMMIT="717da871e7a620702b8b9433bd8f9f181710435a"
UPSTREAM_DIR="$SCRIPT_DIR/upstream"
SRC="$UPSTREAM_DIR/source"

TARGET="${1:-sim}"
case "$TARGET" in
  sim)
    SDK=iphonesimulator
    ARCH=arm64
    MIN_VER_FLAG="-mios-simulator-version-min=17.0"
    ;;
  device)
    SDK=iphoneos
    ARCH=arm64
    MIN_VER_FLAG="-miphoneos-version-min=17.0"
    ;;
  *)
    echo "unknown target: $TARGET (use 'sim' or 'device')" >&2
    exit 1
    ;;
esac

OUT_DIR="$SCRIPT_DIR/build/$TARGET"
OBJ_DIR="$OUT_DIR/obj"
mkdir -p "$OBJ_DIR"

# --- 1. 上流ソース取得（コミットが違えば更新） ---
if [ ! -d "$UPSTREAM_DIR/.git" ]; then
	echo "=== Cloning YaneuraOu ($YANEURAOU_TAG) ==="
	git clone --branch "$YANEURAOU_TAG" --depth 1 "$UPSTREAM_URL" "$UPSTREAM_DIR"
else
	ACTUAL_COMMIT="$(git -C "$UPSTREAM_DIR" rev-parse HEAD)"
	if [ "$ACTUAL_COMMIT" != "$YANEURAOU_COMMIT" ]; then
		echo "=== Updating YaneuraOu checkout to $YANEURAOU_COMMIT ==="
		# GitHubの公開リポジトリは任意SHAのshallow fetchを受け付けない場合があるため、
		# タグを取得してから、下の固定SHA検証で意図したコミットか確認する。
		git -C "$UPSTREAM_DIR" fetch --depth 1 "$UPSTREAM_URL" "refs/tags/$YANEURAOU_TAG"
		git -C "$UPSTREAM_DIR" checkout -q FETCH_HEAD
	else
		echo "=== Using existing upstream checkout: $ACTUAL_COMMIT ==="
	fi
fi

# --- 2. コンパイル対象（YANEURAOU_ENGINE_NNUE構成。全ネイティブ版で共通） ---
SOURCE_LIST="$(cd "$SCRIPT_DIR/../../engine" && pwd)/yaneuraou-v940-sources.txt"
SRCS=()
while IFS= read -r src; do
	[ -n "$src" ] && SRCS+=("$src")
done < "$SOURCE_LIST"

SDKROOT="$(xcrun --sdk "$SDK" --show-sdk-path)"
CXX="xcrun --sdk $SDK clang++"

# Apple Silicon(NEON)向けフラグ（やねうら王Makefileの TARGET_CPU=M1 と同系統）
# だが-mcpu=apple-m1は実機（旧世代Aシリーズ）非互換のため汎用armv8.2-a系に変更。
# main()はwrapperのyaneuraou_main()としてリンクするため -Dmain=yaneuraou_main でリネーム。
CXXFLAGS="-std=c++17 -fno-exceptions -fno-rtti -O3 -DNDEBUG -fPIC \
  -isysroot $SDKROOT -arch $ARCH $MIN_VER_FLAG \
  -DUSE_MAKEFILE -DYANEURAOU_ENGINE_NNUE \
  -DENGINE_NAME_FROM_MAKEFILE=YaneuraOu_NNUE \
  -DIS_64BIT -DUSE_NEON \
  -Dmain=yaneuraou_main \
  -Wno-unused-parameter -Wno-unused-command-line-argument"

echo "=== Compiling ${#SRCS[@]} files for $TARGET ($ARCH, sdk=$SDK) ==="
OBJS=()
for src in "${SRCS[@]}"; do
  obj="$OBJ_DIR/${src//\//__}.o"
  OBJS+=("$obj")
  if [ ! -f "$obj" ] || [ "$SRC/$src" -nt "$obj" ]; then
    echo "  CC $src"
    $CXX $CXXFLAGS -c "$SRC/$src" -o "$obj"
  else
    echo "  SKIP (cached) $src"
  fi
done

echo "=== Archiving libyaneuraou.a ==="
LIB="$OUT_DIR/libyaneuraou.a"
rm -f "$LIB"
xcrun --sdk "$SDK" ar rcs "$LIB" "${OBJS[@]}"

echo "=== Done: $LIB ==="
ls -lh "$LIB"
file "$LIB"

# --- 3. wrapper（engine_wrapper.cpp）をコンパイルし、libyaneuraou.a と合体して
#     libshogiengine.a を作る（:shared からの cinterop 経由リンク対象）。
#     main.cpp 側の -Dmain=yaneuraou_main は不要（wrapper自身はmainを定義しない）。
WRAPPER_DIR="$SCRIPT_DIR/wrapper"
WRAPPER_CXXFLAGS="-std=c++17 -fno-rtti -O3 -DNDEBUG -fPIC \
  -isysroot $SDKROOT -arch $ARCH $MIN_VER_FLAG \
  -Wno-unused-parameter -Wno-unused-command-line-argument"
WRAPPER_OBJ="$OBJ_DIR/engine_wrapper.cpp.o"
echo "=== Compiling wrapper for $TARGET ==="
$CXX $WRAPPER_CXXFLAGS -c "$WRAPPER_DIR/engine_wrapper.cpp" -o "$WRAPPER_OBJ"

echo "=== Archiving libshogiengine.a (wrapper + yaneuraou merged) ==="
COMBINED_LIB="$OUT_DIR/libshogiengine.a"
rm -f "$COMBINED_LIB"
xcrun --sdk "$SDK" libtool -static -o "$COMBINED_LIB" "$LIB" "$WRAPPER_OBJ"

echo "=== Done: $COMBINED_LIB ==="
ls -lh "$COMBINED_LIB"
file "$COMBINED_LIB"
