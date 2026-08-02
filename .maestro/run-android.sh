#!/usr/bin/env bash
# Androidフローの実行入口。ファイル取込フロー（02）はKIFがエミュレータの
# Download配下にありメディアスキャナ認識済みであることが前提になるため、
# 配置もここで一緒に行う。
#
# KIF配置をMaestroフロー内に書かないのは、adb操作（push・broadcast）が
# Maestroのコマンドとして提供されていないため。
#
# 使い方: .maestro/run-android.sh [KIFファイル]
set -euo pipefail
cd "$(dirname "$0")/.."

KIF_FILE="${1:-app/kifu/src/jvmTest/resources/wars_game3.kif}"
DEST="/sdcard/Download/$(basename "${KIF_FILE}")"

adb push "${KIF_FILE}" "${DEST}"
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
  -d "file://${DEST}"

maestro test --platform android .maestro/android/
