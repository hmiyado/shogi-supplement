#!/usr/bin/env bash
# Androidフローの実行入口。ファイル取込フロー（02）はKIFがエミュレータの
# Download配下にありメディアスキャナ認識済みであることが前提になるため、
# 配置もここで一緒に行う。
#
# KIF配置をMaestroフロー内に書かないのは、adb操作（push・broadcast）が
# Maestroのコマンドとして提供されていないため。
#
# ディレクトリ一括ではなくフロー単位で並べているのは、削除まで進むフロー（05・08）が
# 棋譜を消した状態で終わり、解析済みの棋譜を前提にするフローがその後ろでは動かないため。
#
# 使い方: .maestro/run-android.sh [KIFファイル]
#   端末が複数繋がっているときは ANDROID_SERIAL で選ぶ:
#   ANDROID_SERIAL=emulator-5554 .maestro/run-android.sh
set -euo pipefail
cd "$(dirname "$0")/.."

KIF_FILE="${1:-app/kifu/src/jvmTest/resources/wars_game3.kif}"
DEST="/sdcard/Download/$(basename "${KIF_FILE}")"

# エミュレータと実機が同時に繋がっていると、対象を決められずadbもMaestroも止まる。
# adbはANDROID_SERIALを自前で読むが、Maestroは読まないので明示的に渡す。
if [ -n "${ANDROID_SERIAL:-}" ]; then
  MAESTRO_ARGS=(--udid "${ANDROID_SERIAL}")
else
  MAESTRO_ARGS=(--platform android)
fi

adb push "${KIF_FILE}" "${DEST}"
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
  -d "file://${DEST}"

# 03（不正ファイルの取込）用。KIFとして読めないファイルをピッカーから選べる場所に置く。
INVALID_DEST="/sdcard/Download/not_a_kif.txt"
printf 'this is not a kif\n' > "${TMPDIR:-/tmp}/not_a_kif.txt"
adb push "${TMPDIR:-/tmp}/not_a_kif.txt" "${INVALID_DEST}"
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
  -d "file://${INVALID_DEST}"

maestro test "${MAESTRO_ARGS[@]}" .maestro/android/01_home_smoke.yaml
maestro test "${MAESTRO_ARGS[@]}" .maestro/android/02_kif_import_via_file_picker.yaml
maestro test "${MAESTRO_ARGS[@]}" .maestro/android/03_file_import_invalid.yaml
maestro test "${MAESTRO_ARGS[@]}" .maestro/android/04_manual_kif_input.yaml
maestro test "${MAESTRO_ARGS[@]}" .maestro/android/06_manual_kif_side_cancel.yaml
maestro test "${MAESTRO_ARGS[@]}" .maestro/android/07_settings_theme.yaml
maestro test "${MAESTRO_ARGS[@]}" .maestro/android/09_drill_study.yaml
# 05・08はclearStateでリセットしたうえで削除まで進むため、解析済みの棋譜を前提にする
# フローより後ろに置く。
maestro test "${MAESTRO_ARGS[@]}" .maestro/android/05_game_delete.yaml
maestro test "${MAESTRO_ARGS[@]}" .maestro/android/08_game_list.yaml
