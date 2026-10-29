package dev.miyado.shogisupplement.ui.gamelist

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import dev.miyado.shogisupplement.db.GameListFilter

/**
 * レポートを開いている間も保持する棋譜一覧のスクロール位置と読み込み済みページ。
 * 条件・アカウントが変わったときだけ先頭へ戻す。
 */
@Stable
class GameListSession internal constructor(val listState: LazyListState) {
    internal var paged: PagedGameListState? = null
    private var scrollKey: Pair<String?, GameListFilter>? = null

    /** 初回表示では false。以降に条件またはアカウントが変わったときだけ true。 */
    internal fun consumeQueryChange(owner: String?, filter: GameListFilter): Boolean {
        val next = owner to filter
        val previous = scrollKey
        scrollKey = next
        return previous != null && previous != next
    }
}

@Composable
fun rememberGameListSession(): GameListSession {
    val listState = rememberLazyListState()
    return remember(listState) { GameListSession(listState) }
}
