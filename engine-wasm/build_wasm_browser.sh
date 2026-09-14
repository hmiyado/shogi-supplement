#!/bin/bash
# ソース・パッチ・コンパイルフラグ・emsdkイメージタグは事前検証時と同一にする(配信する成果物が、検証で確認した挙動と一致することが前提のため)。
#
# 2変種を作る:
#   - nosimd : SIMD命令を使わない素のスカラーコード
#   - simd   : 上と同じソースに -msimd128 を足しただけ(LLVMの自動ベクトル化に任せる)
#
# シングルスレッド(-pthreadなし)構成: Threads=1が本番の不変条件で、pthread不要=
# SharedArrayBuffer不要=COOP/COEPヘッダなしの静的ホスティングで配信できる。
# v9.40は元々std::threadを生成する作りなので、パッチで最小限の
# 同期実行フォールバックに置き換えている。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 再現性のためタグを固定する(タグの実体はコミットSHAへ固定済み)。3.1.73はamd64のみでApple Siliconだとエミュレーション実行になり遅いため、arm64イメージが提供されている4.0.23を採用(ネイティブ速度で動く)。
EMSDK_IMAGE="emscripten/emsdk:4.0.23"

UPSTREAM_DIR="$SCRIPT_DIR/upstream/YaneuraOu"
OUT_DIR="$SCRIPT_DIR/out-browser"
EVAL_NN_SRC="$SCRIPT_DIR/../app/androidApp/src/main/assets/eval/nn.bin"

"$SCRIPT_DIR/fetch_upstream.sh"

if [ ! -f "$EVAL_NN_SRC" ]; then
	echo "評価関数が見つかりません: $EVAL_NN_SRC" >&2
	exit 1
fi

SOURCE_LIST="$SCRIPT_DIR/../app/engine/yaneuraou-v940-sources.txt"
SRCS=()
while IFS= read -r src; do
	[ -n "$src" ] && SRCS+=("$src")
done < "$SOURCE_LIST"

mkdir -p "$OUT_DIR"
printf '%s\n' "${SRCS[@]}" > "$OUT_DIR/.srcs.txt"

cat > "$OUT_DIR/.compile_link.sh" << 'INNER_SCRIPT'
#!/bin/bash
set -euo pipefail

VARIANT="$1"
SRC_DIR=/work/upstream/YaneuraOu/source
OUT_DIR=/work/out-browser
OBJ_DIR="$OUT_DIR/obj-$VARIANT"
# Why: ソース一覧に含まれないオブジェクトをワイルドカードでリンクしないため、
# variantごとにオブジェクトを再生成する。
rm -rf "$OBJ_DIR"
mkdir -p "$OBJ_DIR"

CXXFLAGS="-std=c++17 -fno-exceptions -fno-rtti -O3 -DNDEBUG \
  -D_LINUX -DUSE_MAKEFILE -DYANEURAOU_ENGINE_NNUE \
  -DENGINE_NAME_FROM_MAKEFILE=YaneuraOu_NNUE \
  -Wno-unused-parameter -Wno-unused-command-line-argument"

if [ "$VARIANT" = "simd" ]; then
	CXXFLAGS="$CXXFLAGS -msimd128"
fi

compile_one() {
	local src="$1"
	local obj="$OBJ_DIR/${src//\//__}.o"
	echo "  CC $src"
	emcc $CXXFLAGS -c "$SRC_DIR/$src" -o "$obj"
}
export -f compile_one
export CXXFLAGS SRC_DIR OBJ_DIR

echo "=== コンパイル(variant=$VARIANT) ==="
xargs -P "$(nproc)" -I{} bash -c 'compile_one "$@"' _ {} < "$OUT_DIR/.srcs.txt"

echo "=== リンク(variant=$VARIANT・ブラウザ向け) ==="
# -sMODULARIZE=1 -sEXPORT_NAME=createYaneuraOu -sENVIRONMENT=worker:
#   Web Worker専用のファクトリ関数を生成する(importScripts()で読み込まれる前提)。
#   "go"は同期ブロッキング呼び出しになるため(シングルスレッド化パッチの割り切り)、
#   メインスレッドではなくWorker上で動かす。
# -sINVOKE_RUN=0 + -sEXPORTED_RUNTIME_METHODS=callMain,FS:
#   評価関数(nn.bin)をfetch→FS.writeFileで書き込んでからcallMain(argv)を
#   明示的に呼ぶ(main()の自動実行は止めておく)。
# -sALLOW_MEMORY_GROWTH=1 -sMAXIMUM_MEMORY=512MiB -sINITIAL_MEMORY=64MiB:
#   USI_Hash=128MB+評価関数(NNUE重み)64MB+探索用バッファを賄う。上限を明示するのは
#   iOS Safari等メモリ制約の厳しい環境でも安全に確保できる範囲に収めるため。
# -sSTACK_SIZE=32MiB, -sNO_EXIT_RUNTIME=1: main()が戻ったあともWorkerのUSI APIを
#   呼べるようランタイムを維持する。
emcc -O3 $([ "$VARIANT" = "simd" ] && echo -msimd128) \
	-sMODULARIZE=1 -sEXPORT_NAME=createYaneuraOu \
	-sENVIRONMENT=worker \
	-sINVOKE_RUN=0 \
	-sEXPORTED_RUNTIME_METHODS=callMain,FS -sNO_EXIT_RUNTIME=1 \
	-sALLOW_MEMORY_GROWTH=1 -sMAXIMUM_MEMORY=536870912 -sINITIAL_MEMORY=67108864 \
	-sSTACK_SIZE=33554432 \
	"$OBJ_DIR"/*.o -o "$OUT_DIR/yaneuraou-$VARIANT.js"

ls -lh "$OUT_DIR/yaneuraou-$VARIANT.js" "$OUT_DIR/yaneuraou-$VARIANT.wasm"

# Why: ブラウザ向け成果物(-sENVIRONMENT=worker)はブラウザAPIに依存するため、
# Node用は同じ.oを使い回して再リンクする。v9.40のWASMはmain()へ渡した
# コマンドラインをUSIコマンド列として処理するため、スモークテストも同じ経路を使う。
# この成果物は配布しない
# (out-browser/node-smoke/、gitignore対象)ため、GPL対応ソースの範囲は変わらない
# (同一ソース・同一パッチの別リンク設定に過ぎない)。
NODE_SMOKE_DIR="$OUT_DIR/node-smoke"
mkdir -p "$NODE_SMOKE_DIR"
echo "=== リンク(variant=$VARIANT・スモークテスト専用Node向け) ==="
emcc -O3 $([ "$VARIANT" = "simd" ] && echo -msimd128) \
	-sMODULARIZE=1 -sEXPORT_NAME=createYaneuraOu \
	-sENVIRONMENT=node -sINVOKE_RUN=0 \
	-sEXPORTED_FUNCTIONS=_main \
	-sEXPORTED_RUNTIME_METHODS=ccall,callMain,FS -sNO_EXIT_RUNTIME=1 \
	-sALLOW_MEMORY_GROWTH=1 -sSTACK_SIZE=33554432 -sNODERAWFS=1 \
	"$OBJ_DIR"/*.o -o "$NODE_SMOKE_DIR/yaneuraou-$VARIANT.js"
INNER_SCRIPT
chmod +x "$OUT_DIR/.compile_link.sh"

for variant in nosimd simd; do
	echo "############################################"
	echo "# ビルド開始(browser): $variant"
	echo "############################################"
	docker run --rm \
		-v "$SCRIPT_DIR:/work" \
		"$EMSDK_IMAGE" \
		bash /work/out-browser/.compile_link.sh "$variant"
done

# 評価関数(nn.bin)をout-browser/へ複製(後続のコピー処理が単一ディレクトリから資産を
# コピーできるようにするため。元のnn.binの複製をリポジトリに追加で持ち込むわけではない
# — out-browser/自体がgitignore対象のビルド成果物)
cp "$EVAL_NN_SRC" "$OUT_DIR/nn.bin"

# VERSIONをout-browser/へ複製(後続の配置処理がバージョン付きパスへの配置に使う。
# 将来CloudFront+S3等へ資産を移す場合も同じVERSIONの値をそのまま使う想定)
cp "$SCRIPT_DIR/VERSION" "$OUT_DIR/VERSION"

echo "=== 完了 (バージョン: $(cat "$SCRIPT_DIR/VERSION")) ==="
echo "生成物: $OUT_DIR/yaneuraou-{nosimd,simd}.{js,wasm}, $OUT_DIR/nn.bin, $OUT_DIR/VERSION"
echo "スモークテスト用: $OUT_DIR/node-smoke/yaneuraou-{nosimd,simd}.js"
