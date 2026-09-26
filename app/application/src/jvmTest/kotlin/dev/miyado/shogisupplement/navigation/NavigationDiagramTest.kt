package dev.miyado.shogisupplement.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigationDiagramTest {
    @Test
    fun diagramIncludesEveryRuntimeTransitionExactlyOnce() {
        val html = navigationDiagramHtml()
        val edgeIds = Regex("\\{id:(\\d+),from:").findAll(html).map { it.groupValues[1].toInt() }.toList()
        assertEquals(NavigationMachine.transitions.indices.toList(), edgeIds)
        NavigationMachine.transitions.forEachIndexed { index, edge ->
            val event = when (edge.event) {
                is NavigationEvent.Open -> "Open"
                NavigationEvent.Back -> "Back"
                NavigationEvent.AnalysisStarted -> "AnalysisStarted"
                NavigationEvent.AnalysisCompleted -> "AnalysisCompleted"
                NavigationEvent.AnalysisClosed -> "AnalysisClosed"
                NavigationEvent.RestoreAuthenticated -> "RestoreAuthenticated"
            }
            assertTrue(html.contains("{id:$index,from:\"${edge.from.name}\",to:\"${edge.to.name}\",event:\"$event\"}"))
        }
        assertFalse(html.contains("<script src="))
        assertTrue(html.contains("id=\"zoom\""))
        assertTrue(html.contains("marker-end"))
    }
}
