package dev.miyado.shogisupplement.util

/**
 * 現在時刻をエポック秒で返す。
 *
 * commonMain では JVM 専用の System.currentTimeMillis() を使えないため
 * expect/actual で分離する（iOS 対応の下準備）。
 */
expect fun currentEpochSeconds(): Long
