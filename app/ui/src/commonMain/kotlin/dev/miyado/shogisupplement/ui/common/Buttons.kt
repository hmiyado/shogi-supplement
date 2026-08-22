package dev.miyado.shogisupplement.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** DESIGN.mdのsecondaryボタン。M3既定との差分は角丸8dpとprimary枠線だけを上書きする。 */
@Composable
fun ShogiSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    // 無効時: DESIGN.mdはdisabled表現を規定していないため、既定のdisabledContentColor
    // （onSurface 38%・下でオーバーライドしない）とトーンを揃え、枠線もM3標準のdisabled
    // outline慣例（onSurface 12%）に合わせる。primaryのアルファ落としにすると文字は灰色・
    // 枠は薄青とちぐはぐになるため避ける。
    val borderColor = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, borderColor),
        contentPadding = contentPadding,
        content = content,
    )
}
