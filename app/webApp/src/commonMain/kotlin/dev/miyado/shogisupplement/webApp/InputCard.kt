package dev.miyado.shogisupplement.webApp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.theme.IbmPlexMonoFamily
import dev.miyado.shogisupplement.ui.theme.shogiColors

@Composable
internal fun InputCard(
    kifText: String,
    onKifTextChange: (String) -> Unit,
    inputError: String?,
    analyzing: Boolean,
    progressDone: Int,
    progressTotal: Int,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shogiColors = MaterialTheme.shogiColors
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = AppStrings.KENTO_KIF_NOTE,
                style = MaterialTheme.typography.labelSmall,
                color = shogiColors.ink3,
            )

            Spacer(Modifier.height(8.dp))
            val text = kifText
            val onTextChange = onKifTextChange
            val placeholder = AppStrings.KENTO_KIF_PLACEHOLDER
            Box {
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    enabled = !analyzing,
                    textStyle = TextStyle(
                        fontFamily = IbmPlexMonoFamily,
                        fontSize = 12.sp,
                        color = shogiColors.ink2,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        // 上限なしだと長い棋譜で下の解析開始ボタンが画面外に押し出されて
                        // 操作不能になるため、超過分は入力欄内のスクロールに任せる。
                        .heightIn(min = 72.dp, max = 220.dp)
                        .border(1.dp, shogiColors.line, RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    decorationBox = { inner ->
                        if (text.isEmpty()) {
                            Text(placeholder, style = MaterialTheme.typography.labelSmall, color = shogiColors.ink3)
                        }
                        inner()
                    },
                )
            }

            if (inputError != null) {
                Spacer(Modifier.height(6.dp))
                Text(inputError, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(10.dp))
            if (analyzing) {
                // 最初の局面結果が届く(progressDone>=1)までは、WorkerがWASMバイナリを
                // 取得中でも進捗が0のまま動かず無応答に見えるため、その間だけ文言を差し替える。
                // 補足行はTextノード自体は常に描画し中身だけ空文字にすることで、
                // 準備中→解析中の切り替わりでカード高さが動かないようにする(No-jitter)。
                val preparing = progressDone == 0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        if (preparing) AppStrings.STUDY_EVAL_PREPARING else AppStrings.kentoAnalyzing(progressDone, progressTotal),
                        style = MaterialTheme.typography.labelSmall,
                        color = shogiColors.ink2,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Text(
                    if (preparing) AppStrings.KENTO_ENGINE_DOWNLOAD_NOTE else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = shogiColors.ink3,
                    modifier = Modifier.padding(start = 24.dp, top = 2.dp),
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(AppStrings.KENTO_CANCEL_BUTTON)
                }
            } else {
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Text(AppStrings.KENTO_ANALYZE_BUTTON)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                AppStrings.KENTO_PRIVACY_NOTE,
                style = MaterialTheme.typography.labelSmall,
                color = shogiColors.ink3,
            )
        }
    }
}
