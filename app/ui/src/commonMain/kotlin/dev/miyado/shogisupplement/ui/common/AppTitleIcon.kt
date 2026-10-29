package dev.miyado.shogisupplement.ui.common

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.ui.theme.LightHighlight
import dev.miyado.shogisupplement.ui.theme.LightInk
import dev.miyado.shogisupplement.ui.theme.LightPrimary

// 図形と配色はランチャーアイコン（ic_launcher_foreground.xml）の駒型カプセルと一致する。
// viewport はその図形の外接矩形＋2単位で、adaptive icon のセーフゾーン余白は含まない。
// テーマに追従させない理由: ランチャー側は OS が色を変えられないため、
// ダークテーマだけ配色を変えると同じアプリの印が2種類になる。
private val AppTitleImageVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "AppTitleIcon",
        defaultWidth = 24.dp,
        defaultHeight = 30.dp,
        viewportWidth = 41.5f,
        viewportHeight = 52f,
    ).apply {
        path(
            fill = SolidColor(LightPrimary),
            stroke = SolidColor(LightInk),
            strokeLineWidth = 2f,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(20.75f, 2f)
            lineTo(34.65f, 8.7f)
            lineTo(37.25f, 27.5f)
            lineTo(4.25f, 27.5f)
            lineTo(6.85f, 8.7f)
            close()
        }
        path(
            fill = SolidColor(LightHighlight),
            stroke = SolidColor(LightInk),
            strokeLineWidth = 2f,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(4.25f, 27.5f)
            lineTo(37.25f, 27.5f)
            lineTo(39.5f, 50f)
            lineTo(2f, 50f)
            close()
        }
    }.build()
}

/** タイトル左に置く小さなアプリアイコン。 */
@Composable
fun AppTitleIcon(modifier: Modifier = Modifier) {
    Image(
        imageVector = AppTitleImageVector,
        contentDescription = null,
        modifier = modifier,
    )
}
