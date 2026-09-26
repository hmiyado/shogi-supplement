#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
OUT_DIR="$ROOT_DIR/docs/promo"
TMP_DIR=$(mktemp -d /private/tmp/shogi-release-1.13.XXXXXX)
trap 'rm -rf "$TMP_DIR"' EXIT

FONT_SANS="$ROOT_DIR/docs/assets/fonts/ibm_plex_sans_jp_regular.ttf"
FONT_SANS_BOLD="$ROOT_DIR/docs/assets/fonts/ibm_plex_sans_jp_bold.ttf"
FONT_MINCHO="$ROOT_DIR/docs/assets/fonts/shippori_mincho_bold.ttf"
FONT_MONO="$ROOT_DIR/docs/assets/fonts/ibm_plex_mono_regular.ttf"
BG='#F7F3EA'
SURFACE='#FFFDF7'
INK='#211E1A'
INK2='#5C564C'
LINE='#DDD5C4'
PRIMARY='#3A4B7C'

magick -size 1200x720 "xc:$BG" \
  -fill "$PRIMARY" -draw 'rectangle 0,0 12,720' \
  -fill "$SURFACE" -stroke "$LINE" -strokewidth 1 \
  -draw 'roundrectangle 66,174 596,300 12,12' \
  -draw 'roundrectangle 66,324 596,450 12,12' \
  -draw 'roundrectangle 66,474 596,600 12,12' \
  -fill "$PRIMARY" -stroke none \
  -draw 'roundrectangle 66,174 71,300 2,2' \
  -draw 'roundrectangle 66,324 71,450 2,2' \
  -draw 'roundrectangle 66,474 71,600 2,2' \
  -font "$FONT_MINCHO" -fill "$INK" -pointsize 38 \
  -draw "text 66,78 '将棋サプリ'" \
  -font "$FONT_MONO" -fill "$PRIMARY" -pointsize 22 \
  -draw "text 66,126 'UPDATE 1.13'" \
  -font "$FONT_MONO" -fill "$PRIMARY" -pointsize 18 \
  -draw "text 94,216 '01'" -draw "text 94,366 '02'" -draw "text 94,516 '03'" \
  -font "$FONT_SANS_BOLD" -fill "$INK" -pointsize 24 \
  -draw "text 148,218 '持ち時間・秒読みの振り返り'" \
  -draw "text 148,368 '絞り込み条件の保存と期間比較'" \
  -draw "text 148,518 '画面のSNS共有'" \
  -font "$FONT_SANS" -fill "$INK2" -pointsize 18 \
  -draw "text 148,258 '残り時間と秒読み中の消費時間を確認'" \
  -draw "text 148,408 '絞り込み条件を保存して比較'" \
  -draw "text 148,558 'レポートなどの画面を画像で共有'" \
  -fill "$INK" -stroke none \
  -draw 'roundrectangle 676,92 1112,654 30,30' \
  "$TMP_DIR/base.png"

magick "$ROOT_DIR/app/androidApp/src/test/snapshots/report_viewer_time_control_transition_same_lane.png" -crop 600x780+0+380 +repage -resize 420x546 "$TMP_DIR/report.png"
magick -size 420x546 xc:none -fill white -draw 'roundrectangle 0,0 419,545 22,22' "$TMP_DIR/mask.png"
magick "$TMP_DIR/report.png" "$TMP_DIR/mask.png" -alpha off -compose CopyOpacity -composite "$TMP_DIR/screen.png"

magick "$TMP_DIR/base.png" \
  "$TMP_DIR/screen.png" -geometry +684+100 -composite \
  -depth 8 -alpha off \
  "$OUT_DIR/release-1.13.png"

echo "generated $OUT_DIR/release-1.13.png"
