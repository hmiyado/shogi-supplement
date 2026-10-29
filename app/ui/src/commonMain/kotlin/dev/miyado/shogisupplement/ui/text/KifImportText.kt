package dev.miyado.shogisupplement.ui.text

import androidx.compose.runtime.Composable
import dev.miyado.shogisupplement.kifu.KifImportController
import dev.miyado.shogisupplement.kifu.KifOrigin
import dev.miyado.shogisupplement.kifu.InvalidKifReason
import dev.miyado.shogisupplement.ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

val KifImportController.Step.Failed.message: String
    @Composable get() = stringResource(when (reason) {
        InvalidKifReason.EMPTY -> if (origin == KifOrigin.CLIPBOARD) Res.string.kif_clipboard_empty else Res.string.kif_file_empty
        InvalidKifReason.INVALID -> if (origin == KifOrigin.CLIPBOARD) Res.string.kif_clipboard_invalid else Res.string.kif_file_invalid
    })
