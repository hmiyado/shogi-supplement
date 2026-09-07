package dev.miyado.shogisupplement.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** iOSはtestTagがそのままaccessibilityIdentifierに出るため、追加の露出設定は要らない。 */
@Composable
actual fun Modifier.exposeTestTags(): Modifier = this
