package dev.miyado.shogisupplement.engine

/** 端末エンジンを同梱しないiOSフレーバーのホスト実装。 */
object IosEngineHost {
    /** engineless フレーバーでは常に false（エンジン入り版は true 固定）。 */
    val ENGINE_LINKED: Boolean = false

    /** engineless フレーバーはエンジンを一切持たないため常に null。 */
    fun getOrCreate(): Engine? = null

    /** ENGINE_LINKEDを確認せず生成した場合は明示的に失敗する。 */
    fun newGameEngineFactory(): () -> Engine = { error("iOS engine unavailable (engineless build)") }

    /** 局終了時の解放。engineless版はエンジンを持たないため no-op（エンジン入り版とシグネチャ互換のみ）。 */
    val keepAliveDispose: (Engine) -> Unit = { /* no-op */ }

    /** ENGINE_LINKEDを確認せず生成した場合は明示的に失敗する。 */
    fun studyEngineFactory(): () -> Engine = { error("iOS engine unavailable (engineless build)") }
}
