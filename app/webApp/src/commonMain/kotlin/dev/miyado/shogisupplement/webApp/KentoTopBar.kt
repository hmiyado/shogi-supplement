package dev.miyado.shogisupplement.webApp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.theme.ShipporiMinchoFamily

private const val BACK_LABEL = "← トップ"
@Composable
internal fun KentoTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            BACK_LABEL,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onBack),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            AppStrings.KENTO_TITLE,
            style = TextStyle(fontFamily = ShipporiMinchoFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp),
        )
    }
}
