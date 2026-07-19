package dev.miyado.shogisupplement.ui.common

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 棋譜ナビゲーション用アイコン（最初へ／最後へ）。
 *
 * material-icons-core（compose.materialIconsExtended）には `|◀`（先頭へ戻す）・
 * `▶|`（末尾へ進める）に相当するアイコンが存在しないため、
 * Material Design Icons の `first_page` / `last_page`（Apache License 2.0）の
 * パスデータを移植し、`materialIcon`/`materialPath`（androidx.compose.material.icons、
 * material-icons-core 同梱の公開ビルダー）で ImageVector として定義する。
 * 出典: Material Design Icons（Apache-2.0）first_page / last_page。
 */
object NavIcons {

    /** 最初へ（|◀ 相当。KifuLineViewer/ReportScreen のナビ先頭ボタン用）。 */
    val FirstPage: ImageVector by lazy {
        materialIcon(name = "NavIcons.FirstPage") {
            materialPath {
                moveTo(18.41f, 16.59f)
                lineTo(13.82f, 12f)
                lineToRelative(4.59f, -4.59f)
                lineTo(17f, 6f)
                lineToRelative(-6f, 6f)
                lineToRelative(6f, 6f)
                close()
            }
            materialPath {
                moveTo(6f, 6f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(12f)
                horizontalLineTo(6f)
                close()
            }
        }
    }

    /** 最後へ（▶| 相当。KifuLineViewer/ReportScreen のナビ末尾ボタン用）。 */
    val LastPage: ImageVector by lazy {
        materialIcon(name = "NavIcons.LastPage") {
            materialPath {
                moveTo(5.59f, 7.41f)
                lineTo(10.18f, 12f)
                lineToRelative(-4.59f, 4.59f)
                lineTo(7f, 18f)
                lineToRelative(6f, -6f)
                lineToRelative(-6f, -6f)
                close()
            }
            materialPath {
                moveTo(16f, 6f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(12f)
                horizontalLineToRelative(-2f)
                close()
            }
        }
    }
}
