package dev.miyado.shogisupplement.navigation

enum class RootTab(val title: String, val destination: AppDestination) {
    HOME("ホーム", AppDestination.HOME),
    GAMES("棋譜", AppDestination.GAME_LIST),
    REPERTOIRE("定跡", AppDestination.REPERTOIRE);

    companion object {
        fun from(destination: AppDestination): RootTab? = entries.firstOrNull { it.destination == destination }
    }
}

data class RootNavigation(val selected: RootTab = RootTab.HOME, val completedGameId: Long? = null) {
    fun completed(gameId: Long, watching: Boolean) = if (watching) this else copy(completedGameId = gameId)
    fun consumeCompletion() = copy(completedGameId = null)
    fun select(tab: RootTab) = copy(selected = tab)
    fun back(current: AppDestination): AppDestination = when {
        RootTab.from(current) != null -> AppDestination.HOME
        NavigationMachine.resolve(current, NavigationEvent.Back) == AppDestination.SETTINGS -> AppDestination.SETTINGS
        else -> NavigationMachine.resolve(current, NavigationEvent.ReturnToTab(selected)) ?: selected.destination
    }
    fun showsTabs(current: AppDestination, editing: Boolean = false): Boolean = RootTab.from(current) != null && !editing
}
