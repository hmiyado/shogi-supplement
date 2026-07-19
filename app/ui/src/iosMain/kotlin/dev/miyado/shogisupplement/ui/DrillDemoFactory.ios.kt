package dev.miyado.shogisupplement.ui

import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.classify.ClassificationResult
import dev.miyado.shogisupplement.db.DatabaseFactory
import dev.miyado.shogisupplement.db.DrillRepository
import dev.miyado.shogisupplement.db.GameRepository
import dev.miyado.shogisupplement.drill.DrillJudge
import dev.miyado.shogisupplement.engine.IosEngineHost
import dev.miyado.shogisupplement.judge.Judgement
import dev.miyado.shogisupplement.judge.VerdictKind
import dev.miyado.shogisupplement.pipeline.BlunderReport
import dev.miyado.shogisupplement.ui.drill.DrillViewModel
import dev.miyado.shogisupplement.util.Logger

/**
 * 実データ駆動の KMP版 DrillViewModel を生成するブートストラップ（factory）。
 *
 * デバッグバイナリ限定で、DBが空のとき最小限のフィクスチャ1問をseedする
 * （初回起動でもドリル画面の動作確認ができるようにする開発用機構。
 * リリースビルドではseedせず、実際の解析結果だけが候補になる）。
 * 2回目以降の起動では既に候補があるため再seedしない（contentHashで判定）。
 */
object DrillDemoFactory {

    private const val SEED_CONTENT_HASH = "ios-demo-drill-seed-v1"

    /**
     * DrillViewModel を生成する。DBが空なら先にフィクスチャをseedする。
     * エンジンは [IosEngineHost] 経由でプロセス内に1つだけ起動し、以降のドリル判定で使い回す
     * （UsiEngineInProcess はプロセス内で一度しか起動できないため。クラスKDoc参照）。
     */
    fun create(): DrillViewModel {
        val gameRepository = DatabaseFactory.gameRepository()
        val drillRepository = DatabaseFactory.drillRepository()
        val settingsRepository = DatabaseFactory.settingsRepository()
        // フィクスチャseedは開発用（デバッグバイナリ限定）。リリースビルドでは
        // 実際の解析結果だけがドリル候補になる（Androidと同じ挙動）。
        @OptIn(kotlin.experimental.ExperimentalNativeApi::class)
        if (kotlin.native.Platform.isDebugBinary) {
            seedIfEmpty(gameRepository, drillRepository)
        }
        return DrillViewModel(
            gameRepository = gameRepository,
            drillRepository = drillRepository,
            settingsRepository = settingsRepository,
            judgeWithEngine = { blunder, userMoveUsi ->
                val engine = IosEngineHost.getOrCreate()
                if (engine != null) {
                    DrillJudge.judge(blunder, userMoveUsi) { sfen -> engine.analyzeSfen(sfen) }
                } else {
                    DrillJudge.DrillResult(
                        isCorrect = false,
                        lossWp = Double.NaN,
                        userMoveUsi = userMoveUsi,
                        bestMoveUsi = blunder.bestUsi,
                        reason = DrillJudge.Reason.ENGINE_EVAL,
                    )
                }
            },
            // 読み筋のオンデマンド延長（結果画面の「最善」タブ）も IosEngineHost の常駐エンジンを
            // 使う。studyEngineFactory は quit() を no-op にする委譲ラッパーを返すため、
            // PvExtensionRunner が延長解析後に無条件で呼ぶ quit() が常駐エンジンを壊さない
            // （ReportViewModel/StudyController と同じ理由。IosEngineHost のKDoc参照）。
            engineFactory = IosEngineHost.studyEngineFactory(),
        )
    }

    private fun seedIfEmpty(gameRepository: GameRepository, drillRepository: DrillRepository) {
        if (gameRepository.getByHash(SEED_CONTENT_HASH) != null) return
        if (drillRepository.getDrillCandidates().isNotEmpty()) return

        // sampleReportBlunder()（MainViewController.kt・ReportViewerScreenshotTest相当）と
        // 同一局面。手筋（両取り・素抜き）の問題で、合法な打ち歩（B*3d）が出題される。
        val sfenBefore = "ln2g3l/2ks1s3/1pppppnr1/p7p/5gpp1/P1P4RP/1PSPPSP2/1KGG5/LN5NL b BPbp 41"
        // sfenBefore の合法性を軽く検証しておく（読み込み不能ならseedしない=ドリルはNoCandidates表示）。
        val boardOk = runCatching { ShogiBoard.fromSfen(sfenBefore) }.isSuccess
        if (!boardOk) {
            Logger.e("DrillDemoFactory", "seed sfenBefore is invalid, skip seeding")
            return
        }

        val report = BlunderReport(
            ply = 41,
            side = "sente",
            moveUsi = "B*3d",
            bestUsi = "2f6f",
            lossWp = 0.225,
            classification = ClassificationResult(
                category = "駒損（タクティクス）",
                diffMaterial = -11,
                punishChecks = 0,
                tookMovedPiece = false,
                missedMateIn = null,
            ),
            judgement = Judgement(
                kind = VerdictKind.TARGET,
                verdict = "○ 出題対象",
                note = "あなたの棋力帯(1600-1899): 約3局に1回",
                problem = "手筋 (両取り・素抜き) の問題",
                priority = 2.9978349024480666,
            ),
            bestPv = "2f6f 8c8d",
            punishPv = "2d2e 2f2e",
            cpBefore = -350,
        )

        runCatching {
            gameRepository.seedFixtureBlunder(
                fileName = "ios_demo_seed.kif",
                contentHash = SEED_CONTENT_HASH,
                rating = 1750,
                coefVersion = "ios-demo-seed",
                report = report,
                sfenBefore = sfenBefore,
                userSide = "sente",
                senteName = "miyado",
                goteName = "相手",
            )
        }.onFailure { e ->
            Logger.e("DrillDemoFactory", "seedFixtureBlunder failed", e)
        }
    }
}

// UsiEngineInProcess の常駐ホルダーは :shared/iosMain の IosEngineHost にある
// （AnalysisOrchestrator の取込フローとドリル判定の両方が同一エンジンインスタンスを
// 共有する必要があるため）。iosApp/ContentView.swift の「Engine」タブ（EngineSmokeRunner）も
// 同一プロセス内で UsiEngineInProcess.companion.create を呼ぶため、実機/シミュレータ検証時に
// 両方を同一プロセスで併用すると2回目の create() が例外になる
// （iOS実動作確認は「CMP」タブのみで行い、「Engine」タブは開かないこと）。
