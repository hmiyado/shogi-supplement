package dev.miyado.shogisupplement.navigation

import kotlinx.coroutines.flow.MutableStateFlow

class NavigationCompletionGuard {
    private val ticket = MutableStateFlow(Any())

    fun snapshot(): Any = ticket.value

    fun invalidate() {
        ticket.value = Any()
    }

    /** 同じ画面訪問の通知だけを、一度だけ消費する。 */
    fun consume(expected: Any): Boolean = ticket.compareAndSet(expected, Any())
}
