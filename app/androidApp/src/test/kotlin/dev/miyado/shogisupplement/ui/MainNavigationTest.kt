package dev.miyado.shogisupplement.ui

import dev.miyado.shogisupplement.navigation.AppDestination
import dev.miyado.shogisupplement.navigation.NavigationEvent
import dev.miyado.shogisupplement.navigation.NavigationMachine
import org.junit.Assert.assertEquals
import org.junit.Test

class MainNavigationTest {
    @Test
    fun settingsChildrenUseSharedTransitions() {
        listOf(MainUiState.Account, MainUiState.Licenses, MainUiState.Debug).forEach { child ->
            assertEquals(child.destination, NavigationMachine.next(
                MainUiState.Settings.destination, NavigationEvent.Open(child.destination)))
            assertEquals(AppDestination.SETTINGS,
                NavigationMachine.next(child.destination, NavigationEvent.Back))
        }
    }

    @Test
    fun homeOpensDrillAndSettings() {
        listOf(MainUiState.Drill, MainUiState.Settings).forEach { target ->
            assertEquals(target.destination, NavigationMachine.next(
                MainUiState.Home(emptyList()).destination, NavigationEvent.Open(target.destination)))
        }
    }
}
