package dev.miyado.shogisupplement.crash

/** クラッシュ・エラーイベント送信の抽象化。extrasにはユーザーデータを含めない。 */
interface CrashReporter {
    /** 例外をクラッシュレポートサービスへ送信する。 @param exception 送信する例外。 @param extras 個人情報を含まないメタデータ。 */
    fun captureException(exception: Throwable, extras: Map<String, String> = emptyMap())
}

/** 何もしない [CrashReporter]。クラッシュレポートサービス未接続のプラットフォーム向け既定値。 */
object NoopCrashReporter : CrashReporter {
    override fun captureException(exception: Throwable, extras: Map<String, String>) {
        // no-op
    }
}
