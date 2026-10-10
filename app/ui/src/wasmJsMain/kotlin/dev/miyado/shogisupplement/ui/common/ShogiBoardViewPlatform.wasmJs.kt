package dev.miyado.shogisupplement.ui.common

import androidx.compose.runtime.Composable
import dev.miyado.shogisupplement.ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal actual fun handRowLabelText(isBlack: Boolean): String =
    stringResource(if (isBlack) Res.string.hand_black_web else Res.string.hand_white_web)
