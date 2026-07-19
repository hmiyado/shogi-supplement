package dev.miyado.shogisupplement.engine

import dev.miyado.shogisupplement.util.Logger
import platform.Foundation.NSBundle

/**
 * UsiEngineInProcess はプロセス内で一度しか起動できない制約（クラスKDoc参照）のため、
 * iOSアプリ全体で1インスタンスに固定するホルダー。
 *
 * AnalysisOrchestrator の取込フローとドリル判定は、いずれもこの単一インスタンスを
 * 共有する必要がある。2つの独立したホルダーが存在すると、2つ目の getOrCreate() が
 * `UsiEngineInProcess.create()` の二重起動ガードに引っかかって例外になるため、
 * 単一インスタンスであることが重要。
 */
object IosEngineHost {
    private var engine: UsiEngineInProcess? = null
    private var attempted = false

    /** エンジンを取得する。初回呼び出し時のみ実際に起動する（以降はキャッシュを返す）。 */
    fun getOrCreate(): UsiEngineInProcess? {
        if (!attempted) {
            attempted = true
            val evalDir = NSBundle.mainBundle.pathForResource("eval", ofType = null)
            if (evalDir != null) {
                engine = runCatching { UsiEngineInProcess.create(evalDir) }
                    .onFailure { e -> Logger.e("IosEngineHost", "engine create failed", e) }
                    .getOrNull()
            } else {
                Logger.e("IosEngineHost", "bundled eval dir not found")
            }
        }
        return engine
    }

    /**
     * [AnalysisOrchestrator] 向けの局ごとのエンジンファクトリ。
     *
     * 常駐インスタンスを返しつつ、[Engine.newGame]（USI "usinewgame"）で局の区切りをつける。
     * `AnalysisRunner` の内部プールは「プールが空のときだけ engineFactory を呼ぶ」ため、
     * 1局につき最初の1回だけ呼ばれる（workers=1と組み合わせて使うこと。既存局面の解析中に
     * 複数ワーカーが同時に呼ぶと newGame が複数回送られる可能性があるため）。
     * エンジン取得に失敗した場合は例外を投げる（呼び出し側の AnalysisOrchestrator が
     * 解析失敗として扱う）。
     */
    fun newGameEngineFactory(): () -> Engine = {
        val e = getOrCreate() ?: error("iOS engine unavailable")
        e.newGame()
        e
    }

    /** 局終了時の解放（quitはしない。プロセス内エンジンを常駐維持する）。 */
    val keepAliveDispose: (Engine) -> Unit = { /* no-op: 次局のために生かしたままにする */ }

    /**
     * ReportViewModel/StudyController（検討モード・読み筋延長）向けのエンジンファクトリ。
     *
     * これらは commonMain 側の実装で、解析後に `engine.quit()` を無条件で呼ぶ設計になっている
     * （Android版 UsiEngineProcess は使い捨てプロセスのため無害）。しかし
     * [UsiEngineInProcess.quit] はプロセス内で一度しか起動できないエンジンスレッドを
     * 実質的に破壊する（クラスKDoc参照。quit後の再startは不可）ため、そのまま
     * [newGameEngineFactory] を渡すと検討モード終了・読み筋延長のたびに常駐エンジンが
     * 死に、以後のドリル判定・取込解析が全滅する。
     *
     * ここでは [quit] を no-op にする委譲ラッパー（[NonQuittingEngine]）を返すことで、
     * ReportViewModel/StudyController から見ると通常どおり quit() できたように振る舞いつつ、
     * 実体（[getOrCreate] の常駐インスタンス）は生かしたままにする。
     */
    fun studyEngineFactory(): () -> Engine = {
        val e = getOrCreate() ?: error("iOS engine unavailable")
        NonQuittingEngine(e)
    }
}

/**
 * [IosEngineHost.studyEngineFactory] が返す委譲ラッパー。[quit] だけを no-op にし、
 * それ以外は常駐エンジンへそのまま委譲する。
 */
private class NonQuittingEngine(private val delegate: Engine) : Engine {
    override fun analyze(moves: List<String>, nodes: Int): List<PvInfo> = delegate.analyze(moves, nodes)

    override fun analyzeSfen(sfen: String, additionalMoves: List<String>, nodes: Int): List<PvInfo> =
        delegate.analyzeSfen(sfen, additionalMoves, nodes)

    override fun newGame() = delegate.newGame()

    override fun quit() { /* no-op: IosEngineHost の常駐エンジンは生かしたままにする */ }
}
