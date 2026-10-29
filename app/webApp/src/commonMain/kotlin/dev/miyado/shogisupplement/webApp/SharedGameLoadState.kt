package dev.miyado.shogisupplement.webApp

import dev.miyado.shogisupplement.gameshare.GameShareResult
import dev.miyado.shogisupplement.gameshare.GameShareSnapshot

sealed interface SharedGameLoadState {
    data object Loading : SharedGameLoadState
    data class Ready(val snapshot: GameShareSnapshot) : SharedGameLoadState
    data object Missing : SharedGameLoadState
    data object Invalid : SharedGameLoadState
    data object Failed : SharedGameLoadState
}

fun GameShareResult<GameShareSnapshot>.toLoadState(): SharedGameLoadState = when (this) {
    is GameShareResult.Success -> SharedGameLoadState.Ready(value)
    GameShareResult.NotFound -> SharedGameLoadState.Missing
    GameShareResult.Invalid -> SharedGameLoadState.Invalid
    else -> SharedGameLoadState.Failed
}
