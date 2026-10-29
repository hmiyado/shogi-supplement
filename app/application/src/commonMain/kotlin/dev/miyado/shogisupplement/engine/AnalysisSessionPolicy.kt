package dev.miyado.shogisupplement.engine

/**
 * バックグラウンド復帰時に、前回の解析を再問い合わせする条件をOS間で共有する。
 * @param isAnalyzing 画面または常駐処理が解析中であること。
 * @param lastProgressAtEpochSeconds 最後に解析進捗を受け取った時刻。未受信ならnull。
 */
fun shouldResumeAnalysisAfterForeground(
    isAnalyzing: Boolean,
    lastProgressAtEpochSeconds: Long?,
    nowEpochSeconds: Long,
    idleThresholdSeconds: Long = DEFAULT_FOREGROUND_RESUME_IDLE_THRESHOLD_SECONDS,
): Boolean {
    if (!isAnalyzing) return false
    if (lastProgressAtEpochSeconds == null) return false
    return nowEpochSeconds - lastProgressAtEpochSeconds >= idleThresholdSeconds
}

const val DEFAULT_FOREGROUND_RESUME_IDLE_THRESHOLD_SECONDS = 5L
