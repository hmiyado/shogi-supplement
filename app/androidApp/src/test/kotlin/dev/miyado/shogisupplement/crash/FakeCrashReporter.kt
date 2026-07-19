package dev.miyado.shogisupplement.crash

import java.util.Collections

/**
 * テスト用 CrashReporter 実装。
 * キャプチャされたイベントを [events] に記録する。実際の送信は行わない。
 * AnalysisRunner は複数ワーカー（Dispatchers.IO）から並行に captureException を
 * 呼ぶため、記録は同期リストで行う（非同期テストのflaky対策）。
 */
class FakeCrashReporter : CrashReporter {

    data class CapturedEvent(
        val exception: Throwable,
        val extras: Map<String, String>,
    )

    val events: MutableList<CapturedEvent> =
        Collections.synchronizedList(mutableListOf())

    override fun captureException(exception: Throwable, extras: Map<String, String>) {
        events.add(CapturedEvent(exception, extras))
    }
}
