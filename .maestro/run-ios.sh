#!/usr/bin/env bash
# iOSフローの実行入口。クリップボードの事前状態（02は有効なKIFが無いこと、
# 03は有効なKIFがあること）が前提のフローがあるため、投入もここで一緒に行う。
#
# KIF投入をMaestroフロー内に書かないのは、MaestroのsetClipboard/pasteTextが
# Maestro内部のクリップボードにしか作用せず、UIPasteboardを直接読む
# アプリの「クリップボードから貼り付け」には効かないため。
# 02と03で前提が相反するため、ディレクトリ一括ではなくフロー単位で
# クリップボードを設定し直して実行する。
#
# 使い方: .maestro/run-ios.sh [UDID] [KIFファイル]
#   UDID省略時はbooted（起動中シミュレータが1台のときのみ）。
set -euo pipefail
cd "$(dirname "$0")/.."

UDID="${1:-booted}"
KIF_FILE="${2:-app/kifu/src/jvmTest/resources/wars_game3.kif}"

if [ "${UDID}" = "booted" ]; then
  MAESTRO_ARGS=(--platform ios)
else
  MAESTRO_ARGS=(--udid "${UDID}")
fi

# LANG/LC_ALLの指定が無いと、CJKテキストで xcrun simctl pbcopy が
# 文字コード判定に失敗する（simctl側の既知の癖）。
put_clipboard() {
  LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8 \
    bash -c "xcrun simctl pbcopy '${UDID}'" <<<"$1"
}

put_clipboard "not a kif"
maestro test "${MAESTRO_ARGS[@]}" .maestro/ios/01_home_smoke.yaml
maestro test "${MAESTRO_ARGS[@]}" .maestro/ios/02_clipboard_paste_alert.yaml

put_clipboard "$(cat "${KIF_FILE}")"
maestro test "${MAESTRO_ARGS[@]}" .maestro/ios/03_kif_import_via_clipboard.yaml
