package dev.miyado.shogisupplement.engine

/**
 * engineless（サーバー解析専用。エンジン・評価関数を一切同梱しない）フレーバー向けの
 * [IosEngineHost] 実装。
 *
 * エンジン入り版（shared/src/iosEngineMain/.../IosEngineHost.kt）と完全に同じ公開API
 * （プロパティ・関数のシグネチャ）を持つダミー実装で、UsiEngineInProcess/cinterop
 * シンボル（dev.miyado.shogisupplement.engine.wrapper.*）を一切参照しない。
 * -PiosEngineless=true ビルドでは iosMain の srcDir がこちらに切り替わり、エンジン入り版の
 * IosEngineHost.kt/UsiEngineInProcess.kt はコンパイル対象そのものから外れる
 * （shared/build.gradle.kts参照）。
 *
 * :ui iosMain（IosMainController/DrillDemoFactory）はこのオブジェクトを唯一の窓口として
 * エンジンを取得しているため、フレーバー間でソース変更なしにコンパイルが通る。
 *
 * Why not error/exception at object init: このオブジェクト自体はどちらのフレーバーでも
 * 参照されるだけで初期化コストが無いに越したことはない。エンジン不在を示すのは
 * 「呼んだら失敗する」ではなく [ENGINE_LINKED] を見て呼び出し側が事前に迂回する設計にしている
 * （[IosMainController.confirmSideAndAnalyze] 参照）。
 */
object IosEngineHost {
    /** engineless フレーバーでは常に false（エンジン入り版は true 固定）。 */
    val ENGINE_LINKED: Boolean = false

    /** engineless フレーバーはエンジンを一切持たないため常に null。 */
    fun getOrCreate(): Engine? = null

    /**
     * [AnalysisOrchestrator] 向けの局ごとのエンジンファクトリ。
     *
     * 呼び出し側（[IosMainController.confirmSideAndAnalyze]）は [ENGINE_LINKED] が false の
     * ときサーバー解析未設定エラーを先に出してこの経路自体に進まない想定のため、
     * このラムダが実行されることは通常無い。万一到達した場合はエンジン入り版の
     * `getOrCreate() ?: error(...)` と同じ失敗のさせ方（例外はAnalysisOrchestratorが
     * Outcome.Failedへ変換する）にする。
     */
    fun newGameEngineFactory(): () -> Engine = { error("iOS engine unavailable (engineless build)") }

    /** 局終了時の解放。engineless版はエンジンを持たないため no-op（エンジン入り版とシグネチャ互換のみ）。 */
    val keepAliveDispose: (Engine) -> Unit = { /* no-op */ }

    /**
     * ReportViewModel/StudyController（検討モード・読み筋延長）向けのエンジンファクトリ。
     * iOSはこれらのUI導線自体を隠していない（MainViewController.kt の
     * `pvExtensionEnabled = false` は読み筋延長のみ）が、検討モード（盤面タップ）は
     * engineless＋ANALYSIS_BASE_URL未設定では現状フォールバック手段が無く、
     * 呼ばれれば [newGameEngineFactory] と同じ理由で例外になる。
     */
    fun studyEngineFactory(): () -> Engine = { error("iOS engine unavailable (engineless build)") }
}
