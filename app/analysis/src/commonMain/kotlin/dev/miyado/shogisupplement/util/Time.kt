package dev.miyado.shogisupplement.util

/** 現在時刻をエポック秒で返す。プラットフォームごとのactual実装で時刻を取得する。 */
expect fun currentEpochSeconds(): Long
