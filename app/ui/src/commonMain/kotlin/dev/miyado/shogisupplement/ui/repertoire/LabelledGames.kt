package dev.miyado.shogisupplement.ui.repertoire

import androidx.compose.runtime.*
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.repertoire.RepertoireRepository
import dev.miyado.shogisupplement.repertoire.RepertoireSync
import dev.miyado.shogisupplement.repertoire.labelledGames
import dev.miyado.shogisupplement.ui.common.defaultIoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

@Composable
fun rememberLabelledGames(games: List<GameRecord>, repository: RepertoireRepository, owner: String?, sync: RepertoireSync?): List<GameRecord> {
    var result by remember(games, owner) { mutableStateOf(games.map { it.copy(positionLabels = emptySet()) }) }
    LaunchedEffect(games, owner) {
        suspend fun refresh() {
            result = withContext(defaultIoDispatcher) {
                owner?.let { repository.labelledGames(it, games) } ?: games.map { it.copy(positionLabels = emptySet()) }
            }
        }
        refresh()
        if (owner != null) {
            try { sync?.synchronize(); refresh() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { }
        }
    }
    return result
}
