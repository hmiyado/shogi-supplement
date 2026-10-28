#!/usr/bin/env bash
# 外部からのクリップボード更新を拒否するSimulatorでも同じ入力を使うため、
# Debug起動引数でアプリ自身に書き込ませる。
# 使い方: .maestro/run-ios.sh [UDID] [KIFファイル]
set -euo pipefail
cd "$(dirname "$0")/.."

UDID="${1:-booted}"
KIF_FILE="${2:-app/kifu/src/jvmTest/resources/wars_game3.kif}"

if [ "${UDID}" = "booted" ]; then
  MAESTRO_ARGS=(--platform ios)
else
  MAESTRO_ARGS=(--udid "${UDID}")
fi

PASTEBOARD_FIXTURE_B64=""
put_clipboard() {
  PASTEBOARD_FIXTURE_B64="$(printf '%s' "$1" | base64 | tr -d '\n')"
}
run_maestro() {
  maestro test "${MAESTRO_ARGS[@]}" -e "UITEST_PASTEBOARD_KIF_BASE64=${PASTEBOARD_FIXTURE_B64}" "$1"
}

put_clipboard "not a kif"
run_maestro .maestro/ios/01_home_smoke.yaml
run_maestro .maestro/ios/02_clipboard_paste_alert.yaml

put_clipboard "$(cat "${KIF_FILE}")"
run_maestro .maestro/ios/03_kif_import_via_clipboard.yaml
run_maestro .maestro/ios/04_manual_kif_input.yaml
run_maestro .maestro/ios/06_manual_kif_side_cancel.yaml
run_maestro .maestro/ios/07_settings_theme.yaml
run_maestro .maestro/ios/09_drill_study.yaml
# 05・08はclearStateでリセットするため、削除対象を用意するクリップボードの中身が
# 引き続き必要（03と同じ前提）。
run_maestro .maestro/ios/05_game_delete.yaml
run_maestro .maestro/ios/08_game_list.yaml
