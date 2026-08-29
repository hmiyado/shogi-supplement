package dev.miyado.shogisupplement.webApp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Why not: 画面はスマホアプリのレイアウトをそのまま流用しており広幅では
 * 破綻するため、コンテンツ幅をモバイル相当に固定して中央寄せする。
 */
@Composable
fun WebMobileLayout(content: @Composable () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(modifier = Modifier.widthIn(max = 430.dp).fillMaxHeight()) {
                content()
            }
        }
    }
}
