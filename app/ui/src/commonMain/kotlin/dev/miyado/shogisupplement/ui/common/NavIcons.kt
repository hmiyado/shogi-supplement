package dev.miyado.shogisupplement.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.vectorResource
import dev.miyado.shogisupplement.ui.generated.resources.*

/** Page navigation uses shared drawable resources on every platform. */
object NavIcons {
    val FirstPage: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_nav_first_page)
    val LastPage: ImageVector
        @Composable get() = vectorResource(Res.drawable.ic_nav_last_page)
}
