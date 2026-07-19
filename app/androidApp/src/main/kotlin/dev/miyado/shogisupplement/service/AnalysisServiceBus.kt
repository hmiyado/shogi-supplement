package dev.miyado.shogisupplement.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * AnalysisService → ViewModel の通知バス。
 * LocalBroadcastManager の代わりに SharedFlow を使うシンプルな実装。
 */
object AnalysisServiceBus {

    private val _events = MutableSharedFlow<ServiceEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ServiceEvent> = _events

    fun emit(event: ServiceEvent) {
        _events.tryEmit(event)
    }

    sealed class ServiceEvent {
        /** 解析完了: DBに保存済みの game_id */
        data class Completed(val gameId: Long) : ServiceEvent()
        /** 解析エラー */
        data class Failed(val message: String) : ServiceEvent()
        /** 進捗更新 */
        data class Progress(val done: Int, val total: Int) : ServiceEvent()
    }
}
