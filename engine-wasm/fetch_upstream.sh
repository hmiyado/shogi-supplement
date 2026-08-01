#!/bin/bash
# GPLv3の対応ソース提供義務を満たすためのビルド一式(engine-wasm/)の一部。
# 上流ソース自体はコミットしない(このリポジトリはGPLv3全文をLICENSEに持つが、
# 上流の全履歴を複製する必要はなく、pinned commitとpatchesがあれば復元可能なため)。
#
# 再現性のため、タグ(v7.00)ではなくそのタグが指すコミットSHAへ直接ピン止めする
# (タグは理論上付け替え得るが、コミットSHAは不変)。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

YANEURAOU_REPO="https://github.com/yaneurao/YaneuraOu.git"
YANEURAOU_COMMIT="0640f43c7efb84630d657e99d6c8b5353062be1c"

UPSTREAM_DIR="$SCRIPT_DIR/upstream/YaneuraOu"
PATCH_DIR="$SCRIPT_DIR/patches"

if [ ! -d "$UPSTREAM_DIR/.git" ]; then
	echo "=== YaneuraOu ${YANEURAOU_COMMIT} を取得 ==="
	mkdir -p "$UPSTREAM_DIR"
	git -C "$UPSTREAM_DIR" init -q
	git -C "$UPSTREAM_DIR" remote add origin "$YANEURAOU_REPO"
	# GitHubはリーチャブルな任意のコミットSHAのfetchを許可している(パブリックリポジトリ)。
	# タグ名でcloneしないのは、タグの指す先が将来変わっても検知できない構成を避けるため。
	git -C "$UPSTREAM_DIR" fetch --depth 1 origin "$YANEURAOU_COMMIT"
	git -C "$UPSTREAM_DIR" checkout -q FETCH_HEAD
else
	echo "=== upstream/YaneuraOu は取得済み。スキップ ==="
fi

ACTUAL_COMMIT="$(git -C "$UPSTREAM_DIR" rev-parse HEAD)"
if [ "$ACTUAL_COMMIT" != "$YANEURAOU_COMMIT" ]; then
	echo "エラー: 取得したコミットが想定と異なります(想定=$YANEURAOU_COMMIT 実際=$ACTUAL_COMMIT)" >&2
	echo "upstream/を削除してから再実行してください。" >&2
	exit 1
fi

shopt -s nullglob
PATCHES=("$PATCH_DIR"/*.patch)
shopt -u nullglob

for p in "${PATCHES[@]}"; do
	name="$(basename "$p")"
	if git -C "$UPSTREAM_DIR" apply --check "$p" 2>/dev/null; then
		echo "=== パッチ適用: $name ==="
		git -C "$UPSTREAM_DIR" apply "$p"
	elif git -C "$UPSTREAM_DIR" apply --check --reverse "$p" 2>/dev/null; then
		echo "=== パッチ適用済み: $name (スキップ) ==="
	else
		echo "パッチが当たりません: $name (upstreamのソースが想定と食い違っています)" >&2
		exit 1
	fi
done

# --- 3. VERSIONファイルとの整合確認(食い違っていても止めない・警告のみ) ---
# VERSIONの形式は "yo-<コミットSHA先頭7桁>-p<パッチ改訂番号>"。
# YANEURAOU_COMMITを更新したのにVERSIONの更新を忘れた場合に気づけるようにする。
VERSION_FILE="$SCRIPT_DIR/VERSION"
if [ -f "$VERSION_FILE" ]; then
	VERSION_CONTENT="$(tr -d '[:space:]' < "$VERSION_FILE")"
	SHORT_COMMIT="${YANEURAOU_COMMIT:0:7}"
	case "$VERSION_CONTENT" in
	"yo-$SHORT_COMMIT-p"*) ;;
	*)
		echo "警告: VERSION($VERSION_CONTENT)が現在のピン止めコミット($SHORT_COMMIT)と整合していません。" >&2
		echo "  上流コミットまたはパッチを変更した場合は engine-wasm/VERSION も更新してください。" >&2
		;;
	esac
fi

echo "=== 完了: $UPSTREAM_DIR (commit $ACTUAL_COMMIT・パッチ適用済み) ==="
