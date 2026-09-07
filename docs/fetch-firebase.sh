#!/usr/bin/env bash
# Firebase JS SDK（App Check用）をkento/vendorへ取り込む。
#
# Why not CDNから実行時に読む: マイページは同じオリジンのlocalStorageへ棋譜の復号鍵を置く。
# 配信元が差し替わればその鍵を読めるスクリプトがページに入る。バージョンを固定して
# 内容ごとリポジトリに持てば、実行時に外部を信頼しなくて済む。
#
# firebase-app-check.js は firebase-app.js を絶対URLで参照している。取り込んだ側は
# 隣のファイルを見るよう書き換える（書き換えないとCDNへ戻ってしまう）。
#
# 使い方:
#   docs/fetch-firebase.sh          取得して SHA256SUMS と突き合わせる
#   docs/fetch-firebase.sh --record 取得して SHA256SUMS を作り直す（版を上げるとき）
set -euo pipefail
cd "$(dirname "$0")"

VERSION="12.18.0"
DEST="kento/vendor/firebase/${VERSION}"
BASE="https://www.gstatic.com/firebasejs/${VERSION}"
FILES="firebase-app.js firebase-app-check.js"

mkdir -p "${DEST}"
for name in ${FILES}; do
  curl -fsS "${BASE}/${name}" -o "${DEST}/${name}"
done

# CDNへの参照を隣のファイルへ向け直す。ハッシュは書き換えたあとの内容に対して取る
# （配信物そのものではなく、実際にページが読むものを固定したいため）。
python3 - "${DEST}/firebase-app-check.js" "${BASE}/firebase-app.js" <<'PY'
import sys
path, cdn_url = sys.argv[1], sys.argv[2]
with open(path, encoding="utf-8") as f:
    body = f.read()
if cdn_url not in body:
    raise SystemExit(f"CDNへの参照が見つかりません: {cdn_url}")
with open(path, "w", encoding="utf-8") as f:
    f.write(body.replace(cdn_url, "./firebase-app.js"))
PY

cd "${DEST}"
if [ "${1:-}" = "--record" ]; then
  shasum -a 256 ${FILES} > SHA256SUMS
  echo "SHA256SUMS を記録した:"
  cat SHA256SUMS
else
  shasum -a 256 -c SHA256SUMS
fi
