package dev.miyado.shogisupplement.crash

/**
 * クラッシュ・エラーイベント送信の抽象化。
 *
 * 本番実装（Android）: SentryCrashReporter（androidApp）
 * テスト実装: FakeCrashReporter（androidApp testソースセット）
 * iOS: 現時点では NoopCrashReporter（Sentry等は未導入）
 *
 * プライバシー原則:
 * - extras にユーザーデータ（棋譜内容・対局者名・レート・SFEN・KIFファイル名）を含めない
 * - sendDefaultPii=false と組み合わせて運用する
 */
interface CrashReporter {
    /**
     * 例外をクラッシュレポートサービスに送信する。
     *
     * @param exception 送信する例外
     * @param extras イベントに付加するメタデータ（プライバシーセーフなもののみ）
     */
    fun captureException(exception: Throwable, extras: Map<String, String> = emptyMap())
}

/** 何もしない [CrashReporter]。クラッシュレポートサービス未接続のプラットフォーム向け既定値。 */
object NoopCrashReporter : CrashReporter {
    override fun captureException(exception: Throwable, extras: Map<String, String>) {
        // no-op
    }
}
