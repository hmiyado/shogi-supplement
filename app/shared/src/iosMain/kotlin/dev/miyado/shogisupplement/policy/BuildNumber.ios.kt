package dev.miyado.shogisupplement.policy

import platform.Foundation.NSBundle

/**
 * Info.plistのCFBundleVersion（iosApp/iosApp/Info.plist）を返す。
 * 数値化できない値（未設定・壊れたInfo.plist等）はfail-open側へ倒すため Int.MAX_VALUE
 * を返す（強制アップデート判定で「build < minBuild」を常に満たさなくする＝ブロックしない）。
 */
actual fun currentBuildNumber(): Int =
    (NSBundle.mainBundle.infoDictionary?.get("CFBundleVersion") as? String)?.toIntOrNull() ?: Int.MAX_VALUE
