package dev.miyado.shogisupplement.ui.report

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.common.ShogiThinTopBar
import dev.miyado.shogisupplement.ui.theme.shogiColors

@Composable
internal fun ReportTopBar(
    title: String,
    onBack: () -> Unit,
    onInfoClick: () -> Unit,
    kifText: String?,
    onCopyKifClick: () -> Unit,
    onDeleteClick: (() -> Unit)?,
) {
    ShogiThinTopBar(title = title, onBack = onBack) {
        // 対局者名（playersLine）は表示しない（対局情報ダイアログと重複するため）。
        IconButton(onClick = onInfoClick, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = AppStrings.GAME_INFO_ICON_DESC,
                modifier = Modifier.size(18.dp),
            )
        }
        if (kifText != null) {
            IconButton(onClick = onCopyKifClick, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = AppStrings.KIF_COPY_ICON_DESC,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (onDeleteClick != null) {
            IconButton(onClick = onDeleteClick, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = AppStrings.GAME_DELETE_ICON_DESC,
                    tint = MaterialTheme.shogiColors.loss,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
