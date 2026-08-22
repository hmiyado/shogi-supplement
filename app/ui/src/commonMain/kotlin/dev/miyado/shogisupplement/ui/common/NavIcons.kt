package dev.miyado.shogisupplement.ui.common

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/** 標準にない「最初へ」「最後へ」のナビゲーションアイコンをMaterialのパスで定義する。 */
object NavIcons {

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
