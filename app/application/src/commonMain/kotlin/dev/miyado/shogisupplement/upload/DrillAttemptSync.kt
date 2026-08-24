package dev.miyado.shogisupplement.upload

/**
 * 未送信の次の一手の成績を送る契約。送信するかどうか（設定・ログイン状態）と
 * 失敗の扱いは実装側が決める。
 */
fun interface DrillAttemptSync {

    suspend fun syncPendingAttempts()
}
