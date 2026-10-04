package dev.miyado.shogisupplement.ui.gamelist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.miyado.shogisupplement.db.GameRecord

@Composable
fun LabelledGamesContent(
    games: List<GameRecord>,
    owner: String?,
    load: suspend () -> List<GameRecord>,
    content: @Composable (List<GameRecord>) -> Unit,
) {
    var ready by remember(games, owner) { mutableStateOf<List<GameRecord>?>(null) }
    LaunchedEffect(games, owner) { ready = load() }
    val result = ready
    if (result == null) {
        Box(Modifier.fillMaxSize().testTag("games_loading"), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else content(result)
}
