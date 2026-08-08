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
import dev.miyado.shogisupplement.engine.Engine
import dev.miyado.shogisupplement.engine.FailoverEngine
import dev.miyado.shogisupplement.engine.IosEngineHost
import dev.miyado.shogisupplement.engine.RemoteAnalysisRunner
import dev.miyado.shogisupplement.engine.WasmStudyEngine
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
     *   非null のときだけローカルWASM優先・不可時はサーバー版の二次判定（[buildSecondaryJudge]）を
     *   使う（IosMainController.confirmSideAndAnalyze と同じ graceful degradation の方針）。
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
            // 読み筋のオンデマンド延長（結果画面の「最善」タブ）のエンジンも二次判定と同じ
            // 出し分け（エンジン入り=常駐エンジン／engineless=ローカルWASM優先・不可時サーバー）。
            // PvExtensionRunner は延長解析後に無条件で quit() を呼ぶため、どの実装も
            // quit() を no-op にして常駐エンジン・常駐ホスト・HTTPクライアントを壊さない。
            engineFactory = buildStudyEngineFactory(authRepository, analysisBaseUrl),
        )
    }

    /**
     * 読み筋延長向けのエンジンファクトリ。エンジン入り版は常駐エンジン、engineless版は
     * サーバー設定があるときだけローカルWASM優先・不可時はサーバー（[FailoverEngine]。
     * IosMainController.studyEngineFactory と同じ出し分け）。どちらも無ければ従来どおり
     * [IosEngineHost.studyEngineFactory] の例外を投げるダミー（engineless版で
     * ANALYSIS_BASE_URL未設定＝出荷前の設定漏れのときだけ到達する経路）。
     */
    private fun buildStudyEngineFactory(
        authRepository: AuthRepository?,
        analysisBaseUrl: String?,
    ): () -> Engine {
        if (IosEngineHost.ENGINE_LINKED || authRepository == null || analysisBaseUrl == null) {
            return IosEngineHost.studyEngineFactory()
        }
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
        val remoteEngine = RemoteStudyEngine { sfen, moves ->
            // サーバー解析はJWT必須のため、未ログインならここで匿名サインインする
            // （通常はドリル到達前に済んでいるはずで、ここに来るのは保険）。
            if (authRepository.currentUser.value == null) {
                authRepository.signInAnonymously()
            }
            runner.analyzePosition(sfen, moves)
        }
        return { FailoverEngine(primary = WasmStudyEngine(), secondary = remoteEngine) }
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
            // ローカルWASMは出題局面・ユーザー手後局面の2回とも無料で解析できるため
            // EngineDrillSecondaryJudge（端末エンジン版と同型・2回解析）を使う。WasmStudyEngine
            // はfail-fastなので、WASMバイナリ未準備等で1回目のanalyzeSfenが即座に例外を投げ、
            // ここでサーバー版（1回だけ解析してクォータを節約するRemoteDrillSecondaryJudge）へ
            // 切り替わる（DrillSecondaryJudge単位のフェイルオーバー。Engine単位で合成すると
            // secondaryも2回解析する構成になりRemoteDrillSecondaryJudgeの節約が失われるため
            // 意図的に分けている）。
            val wasmJudge = EngineDrillSecondaryJudge { sfen -> WasmStudyEngine().analyzeSfen(sfen) }
            return { blunder, userMoveUsi ->
                // IosMainController.confirmSideAndAnalyze と同じ理由: サーバー解析はJWT必須
                // なので、匿名サインインすらしていない初回でも通るよう先に保証する
                // （WASM側はJWT不要だが、フォールバック時に二重で確認する手間を避けるため
                // ここでまとめて保証する）。
                if (authRepository.currentUser.value == null) {
                    authRepository.signInAnonymously()
                }
                try {
                    wasmJudge.judge(blunder, userMoveUsi)
                } catch (e: Exception) {
                    try {
                        remoteJudge.judge(blunder, userMoveUsi)
                    } catch (e2: Exception) {
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
