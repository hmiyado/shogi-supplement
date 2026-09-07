package dev.miyado.shogisupplement.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Web版はUI自動化の対象にしていない。 */
@Composable
actual fun Modifier.exposeTestTags(): Modifier = this
