package dev.miyado.shogisupplement.engine

import dev.miyado.shogisupplement.util.Logger
import platform.Foundation.NSBundle

/**
 * iOSのプロセス内エンジンを単一インスタンスで保持する。
 * エンジンは一度しか起動できないため、解析機能は同じインスタンスを共有する。
 * engineless版は同じ公開APIでnullまたは例外を返す実装に置き換わる。
 * @property ENGINE_LINKED エンジンがリンク済みかを示すフラグ。
 */
object IosEngineHost {
    val ENGINE_LINKED: Boolean = true

    private var engine: Engine? = null
    private var attempted = false

    /** エンジンを取得する。初回だけ起動し、以後はキャッシュを返す。戻り値は共通のEngine型とする。 */
    fun getOrCreate(): Engine? {
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

    /** 局ごとのエンジンファクトリ。常駐インスタンスを返し、newGameで局を区切る。workers=1を前提とする。 */
    fun newGameEngineFactory(): () -> Engine = {
        val e = getOrCreate() ?: error("iOS engine unavailable")
        e.newGame()
        e
    }

    /** 局終了時の解放（quitはしない。プロセス内エンジンを常駐維持する）。 */
    val keepAliveDispose: (Engine) -> Unit = { /* no-op: 次局のために生かしたままにする */ }

    /**
     * 検討・読み筋延長用のエンジンファクトリ。
     * quitをno-opにした委譲ラッパーを返し、常駐エンジンを破棄しない。
     * 通常のEngineとして扱えるため、共通実装の終了処理と両立する。
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
