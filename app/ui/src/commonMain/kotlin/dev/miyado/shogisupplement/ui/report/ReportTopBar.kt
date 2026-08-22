package dev.miyado.shogisupplement.ui.report

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.theme.ShipporiMinchoFamily
import dev.miyado.shogisupplement.ui.theme.shogiColors

@Composable
internal fun ReportTopBar(
    title: String,
    onBack: () -> Unit,
    onInfoClick: () -> Unit,
    kifText: String?,
    onCopyKifClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = AppStrings.BACK,
                modifier = Modifier.size(18.dp),
            )
        }
        // 見出し専用書体（DESIGN.md Typography節）。
        Text(
            text = title,
            style = TextStyle(
                fontFamily = ShipporiMinchoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 2.dp),
        )
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
