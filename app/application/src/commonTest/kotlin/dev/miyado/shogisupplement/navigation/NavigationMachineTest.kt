package dev.miyado.shogisupplement.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class NavigationMachineTest {
    @Test
    fun savedWebReportOpensWithoutAnalysis() {
        assertEquals(AppDestination.REPORT,
            NavigationMachine.resolve(AppDestination.KENTO_INPUT, NavigationEvent.Open(AppDestination.REPORT)))
        assertEquals(null,
            NavigationMachine.resolve(AppDestination.ANALYZING, NavigationEvent.Open(AppDestination.REPORT)))
    }
    @Test
    fun undefinedSameDestinationIsRejected() {
        assertEquals(null, NavigationMachine.resolve(AppDestination.REPORT, NavigationEvent.Open(AppDestination.REPORT)))
        assertEquals(null, NavigationMachine.resolve(AppDestination.HOME, NavigationEvent.Back))
    }
    @Test
    fun webInputAndReanalysisUseTheSameCompletionTransition() {
        listOf(AppDestination.KENTO_INPUT, AppDestination.REPORT).forEach { from ->
            val analyzing = NavigationMachine.next(from, NavigationEvent.AnalysisStarted)
            assertEquals(AppDestination.ANALYZING, analyzing)
            assertEquals(AppDestination.REPORT,
                NavigationMachine.next(analyzing, NavigationEvent.AnalysisCompleted))
        }
    }
    @Test
    fun analysisEventsRequireTheVisibleRequest() {
        assertEquals(true, NavigationMachine.acceptsAnalysisEvent(AppDestination.ANALYZING, "new", "new"))
        assertEquals(false, NavigationMachine.acceptsAnalysisEvent(AppDestination.ANALYZING, "new", "old"))
        assertEquals(false, NavigationMachine.acceptsAnalysisEvent(AppDestination.ANALYZING, null, null))
        assertEquals(false, NavigationMachine.acceptsAnalysisEvent(AppDestination.HOME, "new", "new"))
    }

    @Test
    fun everyTransitionHasOneTarget() {
        assertEquals(NavigationMachine.transitions.size,
            NavigationMachine.transitions.distinctBy { it.from to it.event }.size)
        NavigationMachine.transitions.forEach {
            assertEquals(it.to, NavigationMachine.next(it.from, it.event))
        }
    }

    @Test
    fun completionOnlyNavigatesWhileViewingAnalysis() {
        AppDestination.entries.forEach {
            assertEquals(if (it == AppDestination.ANALYZING) AppDestination.REPORT else it,
                NavigationMachine.next(it, NavigationEvent.AnalysisCompleted))
        }
    }

    @Test
    fun restoredGamesReturnHome() {
        val restored = NavigationMachine.next(AppDestination.SETTINGS, NavigationEvent.RestoreAuthenticated)
        assertEquals(AppDestination.GAME_RESTORE, restored)
        assertEquals(AppDestination.HOME, NavigationMachine.next(restored, NavigationEvent.Back))
    }
}
