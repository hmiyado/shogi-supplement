package dev.miyado.shogisupplement.engine

/**
 * WASMバイナリ配信元URLオーバーライドの識別子と既定値の単一の真実の源。
 * 同値のリテラルをここ以外へ複製しないこと（コメントによる同期指示では
 * 乖離を防げないため、参照で共有する）。
 */
object KentoSiteOverride {
    /** NSUserDefaults（standard）の保存キー。 */
    val defaultsKey: String = "KentoSiteBaseURLOverride"

    /** オーバーライドを注入する環境変数名。 */
    val environmentKey: String = "KENTO_SITE_BASE_URL_OVERRIDE"

    /** オーバーライドが無いときの配信元。 */
    val productionUrl: String = "https://shogi-supplement.miyado.dev/"
}
