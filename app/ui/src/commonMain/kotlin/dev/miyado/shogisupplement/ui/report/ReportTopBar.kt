package dev.miyado.shogisupplement.ui.report

import org.jetbrains.compose.resources.vectorResource

import org.jetbrains.compose.resources.stringResource
import dev.miyado.shogisupplement.ui.generated.resources.*

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.ui.common.ShogiThinTopBar
import dev.miyado.shogisupplement.ui.theme.shogiColors

@Composable
internal fun ReportTopBar(
    title: String,
    onBack: () -> Unit,
    onInfoClick: () -> Unit,
    kifText: String?,
    onCopyKifClick: () -> Unit,
    onShareClick: () -> Unit,
    onDeleteClick: (() -> Unit)?,
) {
    ShogiThinTopBar(title = title, onBack = onBack) {
        // 対局者名（playersLine）は表示しない（対局情報ダイアログと重複するため）。
        IconButton(onClick = onInfoClick, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = vectorResource(Res.drawable.ic_outlined_info),
                contentDescription = stringResource(Res.string.game_info_icon_desc),
                modifier = Modifier.size(18.dp),
            )
        }
        if (kifText != null) {
            IconButton(onClick = onCopyKifClick, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = vectorResource(Res.drawable.ic_outlined_content_copy),
                    contentDescription = stringResource(Res.string.kif_copy_icon_desc),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        IconButton(onClick = onShareClick, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = vectorResource(Res.drawable.ic_outlined_share),
                contentDescription = stringResource(Res.string.share_screen),
                modifier = Modifier.size(18.dp),
            )
        }
        if (onDeleteClick != null) {
            IconButton(onClick = onDeleteClick, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = vectorResource(Res.drawable.ic_outlined_delete),
                    contentDescription = stringResource(Res.string.game_delete_icon_desc),
                    tint = MaterialTheme.shogiColors.loss,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
