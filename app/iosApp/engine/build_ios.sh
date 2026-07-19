#!/bin/bash
# YaneuraOu NNUE(v7.00)をiOS向け静的ライブラリ(libyaneuraou.a)としてビルドする。
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
# 参考: research/scripts/android/build_yaneura_android.sh（Android NDKビルド。同じソース選定・
# YANEURAOU_ENGINE_NNUEの構成をiOS向けclangに移植したもの）
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
UPSTREAM_URL="https://github.com/yaneurao/YaneuraOu.git"
YANEURAOU_TAG="v7.00"
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

# --- 1. 上流ソース取得（未取得なら clone。既にあれば再利用） ---
if [ ! -d "$UPSTREAM_DIR/.git" ]; then
  echo "=== Cloning YaneuraOu ($YANEURAOU_TAG) ==="
  git clone --branch "$YANEURAOU_TAG" --depth 1 "$UPSTREAM_URL" "$UPSTREAM_DIR"
else
  echo "=== Using existing upstream checkout: $(git -C "$UPSTREAM_DIR" describe --tags 2>/dev/null || echo unknown) ==="
fi

# --- 2. コンパイル対象（YANEURAOU_ENGINE_NNUE構成。research/scripts/android/build_yaneura_android.sh と同じ選定） ---
SRCS=(
  main.cpp types.cpp bitboard.cpp misc.cpp movegen.cpp position.cpp
  usi.cpp usi_option.cpp thread.cpp tt.cpp movepick.cpp timeman.cpp
  book/apery_book.cpp book/book.cpp
  extra/bitop.cpp extra/long_effect.cpp extra/sfen_packer.cpp extra/super_sort.cpp
  mate/mate.cpp mate/mate1ply_without_effect.cpp mate/mate1ply_with_effect.cpp mate/mate_solver.cpp
  eval/evaluate_bona_piece.cpp eval/evaluate.cpp eval/evaluate_io.cpp eval/evaluate_mir_inv_tools.cpp
  eval/material/evaluate_material.cpp
  testcmd/benchmark.cpp testcmd/mate_test_cmd.cpp testcmd/normal_test_cmd.cpp testcmd/unit_test.cpp
  eval/nnue/evaluate_nnue.cpp eval/nnue/evaluate_nnue_learner.cpp eval/nnue/nnue_test_command.cpp
  eval/nnue/features/k.cpp eval/nnue/features/p.cpp eval/nnue/features/half_kp.cpp
  eval/nnue/features/half_kp_vm.cpp eval/nnue/features/half_relative_kp.cpp
  eval/nnue/features/half_kpe9.cpp eval/nnue/features/pe9.cpp
  engine/yaneuraou-engine/yaneuraou-search.cpp
)

SDKROOT="$(xcrun --sdk "$SDK" --show-sdk-path)"
CXX="xcrun --sdk $SDK clang++"

# Apple Silicon(NEON)向けフラグ。research/engine/YaneuraOu-NNUE-m1 と同系統（Makefile TARGET_CPU=M1）
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
