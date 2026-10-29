package dev.miyado.shogisupplement.webApp

import dev.miyado.shogisupplement.gameshare.GameShareReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SharedGameReceiverViewModel(
    private val scope: CoroutineScope,
    private val service: GameShareReader,
    private val id: String,
) {
    private val _state = MutableStateFlow<SharedGameLoadState>(SharedGameLoadState.Loading)
    val state: StateFlow<SharedGameLoadState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = SharedGameLoadState.Loading
        scope.launch { _state.value = service.get(id).toLoadState() }
    }
}
