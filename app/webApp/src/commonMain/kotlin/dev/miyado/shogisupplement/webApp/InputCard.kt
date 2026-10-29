package dev.miyado.shogisupplement.webApp

import dev.miyado.shogisupplement.ui.text.UiStrings

import org.jetbrains.compose.resources.stringResource
import dev.miyado.shogisupplement.ui.generated.resources.*

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
                text = stringResource(Res.string.kento_kif_note),
                style = MaterialTheme.typography.labelSmall,
                color = shogiColors.ink3,
            )

            Spacer(Modifier.height(8.dp))
            val text = kifText
            val onTextChange = onKifTextChange
            val placeholder = stringResource(Res.string.kento_kif_placeholder)
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
                        if (preparing) stringResource(Res.string.study_eval_preparing) else UiStrings.kentoAnalyzing(progressDone, progressTotal),
                        style = MaterialTheme.typography.labelSmall,
                        color = shogiColors.ink2,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Text(
                    if (preparing) stringResource(Res.string.kento_engine_download_note) else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = shogiColors.ink3,
                    modifier = Modifier.padding(start = 24.dp, top = 2.dp),
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.kento_cancel_button))
                }
            } else {
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.kento_analyze_button))
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(Res.string.kento_privacy_note),
                style = MaterialTheme.typography.labelSmall,
                color = shogiColors.ink3,
            )
        }
    }
}
