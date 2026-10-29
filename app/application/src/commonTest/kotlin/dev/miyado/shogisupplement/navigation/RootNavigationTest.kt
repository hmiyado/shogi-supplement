package dev.miyado.shogisupplement.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RootNavigationTest {
    @Test fun everyRootCanAddAndAnalyzeGames() {
        RootTab.entries.forEach { tab ->
            assertEquals(AppDestination.MANUAL_KIFU, NavigationMachine.resolve(tab.destination, NavigationEvent.Open(AppDestination.MANUAL_KIFU)))
            assertEquals(AppDestination.ANALYZING, NavigationMachine.resolve(tab.destination, NavigationEvent.AnalysisStarted))
            assertEquals(AppDestination.ANALYZING, NavigationMachine.resolve(tab.destination, NavigationEvent.Open(AppDestination.ANALYZING)))
        }
    }

    @Test fun backgroundCompletionPreservesTheSelectedTab() {
        val state = RootNavigation().select(RootTab.REPERTOIRE).completed(42, watching = false)
        assertEquals(RootTab.REPERTOIRE, state.selected)
        assertEquals(42L, state.completedGameId)
        assertEquals(null, state.consumeCompletion().completedGameId)
        assertEquals(null, RootNavigation().completed(42, watching = true).completedGameId)
    }
    @Test fun detailsReturnToTheirOriginTab() {
        RootTab.entries.forEach { tab ->
            val navigation = RootNavigation().select(tab)
            assertEquals(tab.destination, navigation.back(AppDestination.REPORT))
            assertEquals(tab.destination, navigation.back(AppDestination.ANALYZING))
            assertEquals(AppDestination.SETTINGS, navigation.back(AppDestination.ACCOUNT))
            assertEquals(AppDestination.HOME, navigation.back(tab.destination))
        }
    }
    @Test fun detailAndEditorHideTabs() {
        val navigation = RootNavigation()
        assertTrue(navigation.showsTabs(AppDestination.GAME_LIST))
        assertFalse(navigation.showsTabs(AppDestination.REPORT))
        assertFalse(navigation.showsTabs(AppDestination.REPERTOIRE, editing = true))
    }
}
