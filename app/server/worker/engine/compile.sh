#!/bin/bash
# YaneuraOu (NNUE, v7.00) を x86-64 Linux 向けにコンパイルする。
# analysis-worker Dockerfile の engine-build ステージから呼ばれる想定
# （素のLinux x86-64 + clang++ があればコンテナ外でも同様に動く:
#   ./compile.sh <avx2|sse42> <source_dir> <out_dir>）。
#
# コンパイル対象ファイル一覧は app/iosApp/engine/build_ios.sh と同一
# （YANEURAOU_ENGINE_NNUE構成）。SIMDターゲット別フラグは本家 source/Makefile の
# TARGET_CPU=AVX2 / SSE42 定義から転記（BMI2はAVX2相当のIntel/AMD世代なら大抵使えるため
# 本家デフォルトのAVX2定義に倣う。ZEN系[BMI2無効]は対象外＝Cloud Run/一般的な
# x86-64サーバーを想定）。
#
# sse42を残す理由: AVX2ビルドがCloud Run上で問題を起こした場合に、同一ソース・
# 同一探索木（ノード数固定なので結果は同一・速度＝コストだけの差）でSIMDターゲットだけ
# 切り替えて出せるようにするため（Dockerfileの --build-arg SIMD_TARGET=sse42 で選択）。
set -euo pipefail

SIMD_TARGET="${1:?usage: compile.sh <avx2|sse42> <source_dir> <out_dir>}"
SRC="${2:?usage: compile.sh <avx2|sse42> <source_dir> <out_dir>}"
OUT_DIR="${3:?usage: compile.sh <avx2|sse42> <source_dir> <out_dir>}"

case "$SIMD_TARGET" in
  avx2)
    SIMD_FLAGS="-DUSE_AVX2 -DUSE_BMI2 -mbmi -mbmi2 -mavx2 -march=corei7-avx"
    ;;
  sse42)
    SIMD_FLAGS="-DUSE_SSE42 -msse4.2 -march=corei7"
    ;;
  *)
    echo "unknown SIMD target: $SIMD_TARGET (use 'avx2' or 'sse42')" >&2
    exit 1
    ;;
esac

CXX=clang++
OBJ_DIR="$OUT_DIR/obj-$SIMD_TARGET"
mkdir -p "$OBJ_DIR"

# YANEURAOU_ENGINE_NNUE構成のソース一覧
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

# -D_LINUX: 本家MakefileがWindows以外のビルドに付与するシンボル(ファイルパス区切り等の分岐)。
# IS_64BITはconfig.hが `__GNUC__ && __x86_64__` から自動定義するため明示不要（ARM/iOS/Androidビルドとの違い）。
CXXFLAGS="-std=c++17 -fno-exceptions -fno-rtti -O3 -DNDEBUG -fPIE \
  -D_LINUX \
  -DUSE_MAKEFILE -DYANEURAOU_ENGINE_NNUE \
  -DENGINE_NAME_FROM_MAKEFILE=YaneuraOu_NNUE \
  $SIMD_FLAGS \
  -Wno-unused-parameter -Wno-unused-command-line-argument"

JOBS="$(nproc)"
echo "=== Compiling ${#SRCS[@]} files (target=$SIMD_TARGET, jobs=$JOBS) ==="

# Why not 逐次コンパイル: Cloud Runイメージのビルド時間に直結するため、コア数ぶん並列化する。
compile_one() {
  local src="$1"
  local obj="$OBJ_DIR/${src//\//__}.o"
  echo "  CC $src"
  $CXX $CXXFLAGS -c "$SRC/$src" -o "$obj"
}
export -f compile_one
export CXX CXXFLAGS SRC OBJ_DIR

OBJS=()
for src in "${SRCS[@]}"; do
  OBJS+=("$OBJ_DIR/${src//\//__}.o")
done

# 1ファイルでも失敗したら中断する（xargsは失敗時に非0を返す）。
printf '%s\n' "${SRCS[@]}" | xargs -P "$JOBS" -I{} bash -c 'compile_one "$@"' _ {}

BIN="$OUT_DIR/YaneuraOu-NNUE-linux-$SIMD_TARGET"
echo "=== Linking $BIN ==="
$CXX -fPIE -pie -o "$BIN" "${OBJS[@]}" -lpthread

echo "=== Done ==="
ls -lh "$BIN"
file "$BIN" || true
