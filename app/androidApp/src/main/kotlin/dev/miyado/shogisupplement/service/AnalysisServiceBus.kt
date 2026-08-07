package dev.miyado.shogisupplement.service

import dev.miyado.shogisupplement.engine.PvInfo
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
        /**
         * 解析完了: DBに保存済みの game_id。
         * @param alreadyExisted true = content_hashが既存レコードと一致し再解析をスキップした
         */
        data class Completed(val gameId: Long, val alreadyExisted: Boolean = false) : ServiceEvent()
        /** 解析エラー */
        data class Failed(val message: String) : ServiceEvent()
        /**
         * 局面ごとの中間結果（プログレッシブ解析表示用）。通知欄の進捗表示は
         * AnalysisService内で完結しており本バスを経由しないため、進捗率だけの
         * イベントは持たない（ViewModel側は受信のたびProgressiveReportStateへ
         * 畳み込み、doneCountをそこから導出する）。
         */
        data class PositionResult(val ply: Int, val pvs: List<PvInfo>) : ServiceEvent()
    }
}
