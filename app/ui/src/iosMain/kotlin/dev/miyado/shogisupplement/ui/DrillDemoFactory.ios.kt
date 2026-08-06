package dev.miyado.shogisupplement.ui

import dev.miyado.shogisupplement.auth.AuthRepository
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.classify.ClassificationResult
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.db.DatabaseFactory
import dev.miyado.shogisupplement.db.DrillRepository
import dev.miyado.shogisupplement.db.GameRepository
import dev.miyado.shogisupplement.drill.DrillJudge
import dev.miyado.shogisupplement.drill.EngineDrillSecondaryJudge
import dev.miyado.shogisupplement.drill.RemoteDrillSecondaryJudge
import dev.miyado.shogisupplement.engine.IosEngineHost
import dev.miyado.shogisupplement.engine.RemoteAnalysisRunner
import dev.miyado.shogisupplement.judge.Judgement
import dev.miyado.shogisupplement.judge.VerdictKind
import dev.miyado.shogisupplement.pipeline.BlunderReport
import dev.miyado.shogisupplement.ui.drill.DrillViewModel
import dev.miyado.shogisupplement.util.Logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout

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

    // ドリルの二次判定（単発局面）向けタイムアウト。IosMainController の解析用HttpClientは
    // 1局まるごとの解析（数十秒〜）を想定した値（10分/5分）だが、ここは1局面だけなので
    // 短い値で十分（かつ短いほうがUXとしても待たせすぎない）。
    private const val POSITION_REQUEST_TIMEOUT_MS = 30_000L
    private const val POSITION_SOCKET_TIMEOUT_MS = 30_000L

    /**
     * DrillViewModel を生成する。DBが空なら先にフィクスチャをseedする。
     *
     * @param authRepository   null（既定）= Supabase未設定ビルド。二次判定は常に端末エンジン版。
     * @param analysisBaseUrl  null（既定）= サーバー解析未設定。[authRepository] と両方が
     *   非null のときだけサーバー版の二次判定（[RemoteDrillSecondaryJudge]）を使う
     *   （IosMainController.confirmSideAndAnalyze と同じ graceful degradation の方針）。
     */
    fun create(
        authRepository: AuthRepository? = null,
        analysisBaseUrl: String? = null,
    ): DrillViewModel {
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
            judgeWithEngine = buildSecondaryJudge(authRepository, analysisBaseUrl),
            // 読み筋のオンデマンド延長（結果画面の「最善」タブ）も IosEngineHost の常駐エンジンを
            // 使う。studyEngineFactory は quit() を no-op にする委譲ラッパーを返すため、
            // PvExtensionRunner が延長解析後に無条件で呼ぶ quit() が常駐エンジンを壊さない
            // （ReportViewModel/StudyController と同じ理由。IosEngineHost のKDoc参照）。
            engineFactory = IosEngineHost.studyEngineFactory(),
        )
    }

    /** 二次判定（曖昧領域のみ呼ばれる）を、サーバー設定の有無で端末エンジン版/サーバー版に出し分ける。 */
    private fun buildSecondaryJudge(
        authRepository: AuthRepository?,
        analysisBaseUrl: String?,
    ): suspend (BlunderRecord, String) -> DrillJudge.DrillResult {
        if (authRepository != null && analysisBaseUrl != null) {
            val httpClient = HttpClient(Darwin) {
                install(HttpTimeout) {
                    requestTimeoutMillis = POSITION_REQUEST_TIMEOUT_MS
                    socketTimeoutMillis = POSITION_SOCKET_TIMEOUT_MS
                }
            }
            val runner = RemoteAnalysisRunner(
                baseUrl = analysisBaseUrl,
                accessTokenProvider = {
                    checkNotNull(authRepository.accessToken()) { "アクセストークンが取得できない" }
                },
                platform = "ios",
                httpClient = httpClient,
                appCheckTokenProvider = AppCheckTokenBridge::getToken,
            )
            val remoteJudge = RemoteDrillSecondaryJudge { sfen -> runner.analyzePosition(sfen) }
            return { blunder, userMoveUsi ->
                try {
                    // IosMainController.confirmSideAndAnalyze と同じ理由: サーバー解析はJWT必須
                    // なので、匿名サインインすらしていない初回でも通るよう先に保証する。
                    if (authRepository.currentUser.value == null) {
                        authRepository.signInAnonymously()
                    }
                    remoteJudge.judge(blunder, userMoveUsi)
                } catch (e: Exception) {
                    // ネットワーク断・401等: 不正解として返す（Androidのエンジン起動失敗時と同じ方針）
                    DrillJudge.DrillResult(
                        isCorrect = false,
                        lossWp = Double.NaN,
                        userMoveUsi = userMoveUsi,
                        bestMoveUsi = blunder.bestUsi,
                        reason = DrillJudge.Reason.ENGINE_EVAL,
                    )
                }
            }
        }

        return { blunder, userMoveUsi ->
            val engine = IosEngineHost.getOrCreate()
            if (engine != null) {
                EngineDrillSecondaryJudge { sfen -> engine.analyzeSfen(sfen) }.judge(blunder, userMoveUsi)
            } else {
                DrillJudge.DrillResult(
                    isCorrect = false,
                    lossWp = Double.NaN,
                    userMoveUsi = userMoveUsi,
                    bestMoveUsi = blunder.bestUsi,
                    reason = DrillJudge.Reason.ENGINE_EVAL,
                )
            }
        }
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
