package dev.miyado.shogisupplement.policy

/**
 * Webはストアの配布物ではなくビルド番号を持たない。強制アップデート判定を
 * 常に通すため fail-open 側（Int.MAX_VALUE）を返す。
 */
actual fun currentBuildNumber(): Int = Int.MAX_VALUE
