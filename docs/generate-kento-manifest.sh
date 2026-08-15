#!/bin/bash
# 検討ページ資産のSHA-256マニフェストを生成する（copy-kento-assets.sh の最後に呼ばれる）。
#
# 対象ディレクトリ配下を全列挙してハッシュを採る。アプリが取得する分だけを列挙しないのは、
# 対象一覧をもう1つ持つとアプリ側・コピースクリプト側との三重管理になるため。
# 余分に載っていても、照合する側は自分が必要とするパスだけを引く。
#
# 使い方: generate-kento-manifest.sh <docsディレクトリ> <出力先パス>
set -euo pipefail

DOCS_DIR="${1:?docsディレクトリを指定してください}"
OUT="${2:?出力先パスを指定してください}"

if [ ! -d "$DOCS_DIR/kento" ]; then
  echo "エラー: $DOCS_DIR/kento がありません" >&2
  exit 1
fi

# 出力先が対象ディレクトリの中にある場合、自分自身を載せないよう除外する
# （生成の前後でハッシュが変わり、検証が原理的に成立しないため）。
OUT_REL=""
case "$OUT" in
  "$DOCS_DIR"/*) OUT_REL="${OUT#"$DOCS_DIR"/}" ;;
esac

{
  echo '{'
  echo '  "files": {'
  first=1
  while IFS= read -r path; do
    rel="${path#"$DOCS_DIR"/}"
    [ "$rel" = "$OUT_REL" ] && continue
    sha="$(shasum -a 256 "$path" | cut -d' ' -f1)"
    [ $first -eq 0 ] && echo ','
    first=0
    printf '    "%s": "%s"' "$rel" "$sha"
  done < <(find "$DOCS_DIR/kento" "$DOCS_DIR/kento-assets" -type f 2>/dev/null | LC_ALL=C sort)
  echo
  echo '  }'
  echo '}'
} > "$OUT"

echo "マニフェスト生成: $OUT ($(grep -c '": "' "$OUT") ファイル)"
