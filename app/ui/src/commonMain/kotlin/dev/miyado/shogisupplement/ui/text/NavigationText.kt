package dev.miyado.shogisupplement.ui.text

import androidx.compose.runtime.Composable
import dev.miyado.shogisupplement.navigation.RootTab
import dev.miyado.shogisupplement.repertoire.PositionLabelScope
import dev.miyado.shogisupplement.ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

val RootTab.title: String
    @Composable get() = stringResource(when (this) {
        RootTab.HOME -> Res.string.root_tab_home
        RootTab.GAMES -> Res.string.root_tab_games
        RootTab.REPERTOIRE -> Res.string.root_tab_repertoire
    })

val PositionLabelScope.title: String
    @Composable get() = stringResource(when (this) {
        PositionLabelScope.ALL -> Res.string.label_scope_all
        PositionLabelScope.BLACK -> Res.string.label_scope_black
        PositionLabelScope.WHITE -> Res.string.label_scope_white
    })
