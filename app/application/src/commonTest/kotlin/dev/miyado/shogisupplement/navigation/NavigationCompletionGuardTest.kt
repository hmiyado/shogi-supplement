package dev.miyado.shogisupplement.navigation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigationCompletionGuardTest {
    @Test
    fun leavingAndReturningDoesNotReviveOldCompletion() {
        val guard = NavigationCompletionGuard()
        val old = guard.snapshot()
        guard.invalidate()
        guard.invalidate()
        assertFalse(guard.consume(old))
        assertTrue(guard.consume(guard.snapshot()))
    }

    @Test
    fun completionCanOnlyBeConsumedOnce() {
        val guard = NavigationCompletionGuard()
        val ticket = guard.snapshot()
        assertTrue(guard.consume(ticket))
        assertFalse(guard.consume(ticket))
    }
}
