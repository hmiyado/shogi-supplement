package dev.miyado.shogisupplement.navigation

enum class RootTab(val destination: AppDestination) {
    HOME(AppDestination.HOME),
    GAMES(AppDestination.GAME_LIST),
    REPERTOIRE(AppDestination.REPERTOIRE);

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
