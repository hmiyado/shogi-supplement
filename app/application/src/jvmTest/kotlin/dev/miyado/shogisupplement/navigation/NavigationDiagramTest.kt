package dev.miyado.shogisupplement.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigationDiagramTest {
    @Test
    fun everyDestinationExportsItsSharedMetadata() {
        val html = navigationDiagramHtml()
        AppDestination.entries.forEach {
            val metadata = "{id:\"${it.name}\",label:\"${it.label}\",group:\"${it.group.name}\",groupLabel:\"${it.group.label}\",isError:${it.isError}}"
            assertEquals(1, html.windowed(metadata.length).count { part -> part == metadata })
        }
        assertFalse(html.contains("const sections=["))
        assertFalse(html.contains("const labels={"))
    }

    @Test
    fun diagramIncludesEveryRuntimeTransitionExactlyOnce() {
        val html = navigationDiagramHtml()
        val edgeIds = Regex("\\{id:(\\d+),from:").findAll(html).map { it.groupValues[1].toInt() }.toList()
        assertEquals(NavigationMachine.transitions.indices.toList(), edgeIds)
        NavigationMachine.transitions.forEachIndexed { index, edge ->
            val event = when (edge.event) {
                is NavigationEvent.Open -> "Open"
                is NavigationEvent.AnalysisCancelled -> "AnalysisCancelled"
                is NavigationEvent.AnalysisFailed -> "AnalysisFailed"
                is NavigationEvent.ReturnToTab -> "ReturnToTab(${edge.event.tab.title})"
                is NavigationEvent.SelectTab -> "SelectTab(${edge.event.tab.title})"
                NavigationEvent.Back -> "Back"
                NavigationEvent.AnalysisStarted -> "AnalysisStarted"
                NavigationEvent.AnalysisCompleted -> "AnalysisCompleted"
                NavigationEvent.AnalysisClosed -> "AnalysisClosed"
                NavigationEvent.RestoreAuthenticated -> "RestoreAuthenticated"
            }
            assertTrue(html.contains("{id:$index,from:\"${edge.from.name}\",to:\"${edge.to.name}\",event:\"$event\",kind:\"${edge.kind.name}\"}"))
        }
        assertFalse(html.contains("<script src="))
        assertTrue(html.contains("id=\"zoom\""))
        assertTrue(html.contains("marker-end"))
    }
}
