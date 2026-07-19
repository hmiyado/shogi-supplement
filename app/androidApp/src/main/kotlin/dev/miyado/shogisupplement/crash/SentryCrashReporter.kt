package dev.miyado.shogisupplement.crash

import io.sentry.Sentry

/**
 * Sentry を使ったクラッシュレポート実装。
 *
 * Sentry.init は [dev.miyado.shogisupplement.ShogiApp.onCreate] で実行済みであること。
 */
class SentryCrashReporter : CrashReporter {

    override fun captureException(exception: Throwable, extras: Map<String, String>) {
        Sentry.captureException(exception) { scope ->
            extras.forEach { (key, value) -> scope.setExtra(key, value) }
        }
    }
}
