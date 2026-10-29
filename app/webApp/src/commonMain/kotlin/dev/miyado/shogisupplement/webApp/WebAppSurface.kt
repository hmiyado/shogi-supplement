package dev.miyado.shogisupplement.webApp

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Web版の地。幅の上限は各画面が決める（本文は最大幅で中央、レポートは広い画面で2ペイン）。 */
@Composable
fun WebAppSurface(content: @Composable () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        content()
    }
}
