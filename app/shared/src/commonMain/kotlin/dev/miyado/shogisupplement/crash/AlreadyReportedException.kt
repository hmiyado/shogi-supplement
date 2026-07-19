package dev.miyado.shogisupplement.crash

/**
 * CrashReporter への送信済みを示すマーカー例外。
 *
 * AnalysisRunner がワーカー内で捕捉・送信した例外をこれで包んで再スローし、
 * 上位（AnalysisService/AnalysisOrchestrator）は原因チェーンに本クラスがあれば
 * 再送信しない（二重送信の抑止）。コルーチンの stack trace recovery で例外が
 * コピーされる場合があるため、判定はインスタンス同一性ではなく原因チェーン内の型で行うこと
 * （[isAlreadyReported]）。
 */
class AlreadyReportedException(cause: Throwable) : Exception(cause.message, cause)

/** 原因チェーンのどこかで送信済みマーカーが付いていれば true。 */
fun Throwable.isAlreadyReported(): Boolean =
    generateSequence(this) { it.cause }.any { it is AlreadyReportedException }
