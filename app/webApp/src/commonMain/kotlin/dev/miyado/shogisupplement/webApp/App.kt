package dev.miyado.shogisupplement.webApp

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.miyado.shogisupplement.ui.theme.ShogiTheme

@Composable
fun App() {
    ShogiTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Text("棋譜を検討する")
        }
    }
}
