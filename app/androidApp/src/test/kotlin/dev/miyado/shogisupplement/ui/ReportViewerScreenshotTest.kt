package dev.miyado.shogisupplement.ui

import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import androidx.compose.material3.Surface
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import dev.miyado.shogisupplement.blunder.PositionEvalDisplay
import dev.miyado.shogisupplement.board.ShogiSquare
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.db.PositionEvalRow
import dev.miyado.shogisupplement.ui.report.MoveListSheet
import dev.miyado.shogisupplement.ui.report.ReportScreen
import dev.miyado.shogisupplement.ui.report.StudyEvalState
import dev.miyado.shogisupplement.ui.report.StudyState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 棋譜ビューア型レポート画面の VRT（スクリーンショットテスト）。
 *
 * 画面下部（罫線より下）は ReportBodyMode で排他表示: 既定は SUMMARY（グラフ＋
 * 悪手サマリー）。悪手（グラフの朱マーカー・悪手カード）を選ぶか「悪手一覧を見る」で
 * LIST（本譜/最善の変化タブ＋既存のカードリスト）に切り替わる。initialSelectedIndex
 * 指定時は自動的に LIST（選択済み状態を再現するため）。選択なしで LIST だけを
 * 再現したいときは initialBodyModeList = true を使う。
 *
 * - report_viewer_mainline: 既定表示（SUMMARY）・初期局面
 * - report_viewer_mainline_flipped: 既定表示（SUMMARY）・後手視点（flip=true）
 * - report_viewer_no_blunders: 既定表示（SUMMARY）・悪手なし
 * - report_viewer_with_eval: 既定表示（SUMMARY）・評価値グラフあり
 * - report_viewer_eval_graph_and_match_rate: 評価値グラフ＋エンジン一致率
 *   （悪手位置の朱マーカー・詰み絡み点のクランプ表示・現在手ライン=2手目を含む）
 * - report_viewer_eval_graph_flipped_gote: 後手ユーザーでのグラフ符号反転
 *   （上=自分有利になることを確認）
 * - report_viewer_list_no_selection: 「悪手一覧を見る」導線で選択なしに LIST へ
 *   （サマリーへ戻る行＋タブ＋一覧）
 * - report_viewer_best_pv_end: LIST・最善の変化タブ・ライン末尾（ナビラベルに形勢サフィックス、
 *   ▶ボタンが「▶+」primary色に変わる）
 * - report_viewer_best_pv_mid: LIST・最善の変化タブ・中間局面（ナビラベルに形勢サフィックス）
 * - report_viewer_best_pv_mate: LIST・最善の変化タブ・中間局面・詰み絡み cp_before
 *   （「詰み」規約表示。ナビラベルのサフィックスとして表示）
 * - report_viewer_study_selection: 検討モード・選択マス＋合法手ドット表示（着手前・
 *   検討ナビラベルに手番ヒントをサフィックス表示）
 * - report_viewer_study_eval: 検討モード・1手指した後の評価値表示（ナビラベルに統合）
 * - report_viewer_study_banner: 検討中バナー（最善の変化タブ起点）＋解析中サフィックス「（…）」
 *
 * トップバーは32dpインライン行（TopAppBar 64dpは使わない）。計器行（評価値行/
 * 「この変化の形勢」行/検討評価行/▶ヒント行/スピナー・エラー行）は持たず、ナビ行に統合する。
 * 座標ラベル余白は2dp。
 *
 * ゴールデン更新: ./gradlew :androidApp:recordRoborazziDebug
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w400dp-h800dp-xxhdpi",
    application = android.app.Application::class,
)
class ReportViewerScreenshotTest {

    @OptIn(ExperimentalRoborazziApi::class)
    private val roborazziOptions = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5),
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
    )

    private fun sampleGame() = GameRecord(
        id = 1L,
        fileName = "miyado_game1.kif",
        contentHash = "hash1",
        moveCount = 74L,
        senteName = "miyado",
        goteName = "相手",
        analyzedAt = 1_780_000_000L,
        rating = 1750L,
        coefVersion = "hao_v1",
        // 最初の4手だけ登録（初期局面→41手目のテストは別途）
        movesUsi = listOf("7g7f", "3c3d", "2g2f", "8c8d"),
        userSide = "sente",
    )

    private fun sampleBlunder() = BlunderRecord(
        id = 1L,
        gameId = 1L,
        ply = 41L,
        side = "sente",
        moveUsi = "B*3d",
        bestUsi = "2f6f",
        lossWp = 0.225,
        sfenBefore = "ln2g3l/2ks1s3/1pppppnr1/p7p/5gpp1/P1P4RP/1PSPPSP2/1KGG5/LN5NL b BPbp 41",
        category = "駒損（タクティクス）",
        diffMaterial = -11L,
        punishChecks = 0L,
        tookMovedPiece = false,
        missedMateIn = null,
        verdict = "○ 出題対象",
        note = "あなたの棋力帯(偏差値47-59): 約3局に1回",
        problemType = "手筋 (両取り・素抜き) の問題",
        priority = 2.9978349024480666,
        // bestPv は sfenBefore から見て完全に合法な手順でなければならない。"2f6f 2d2e" だと
        // 2手目 "2d2e" の移動元 (file2,rank4) に駒がなく非合法となり、JapaneseNotation.format が
        // 例外を投げてナビラベルが生USI「2d2e」にフォールバックしてしまう。"2f6f 8c8d" を使い、
        // report_viewer_best_pv_end golden が正しい日本語表記「42手目 △８四歩」で
        // 描画されることを確認する。
        bestPv = "2f6f 8c8d",
        punishPv = "2d2e 2f2e",
        // 最善の変化タブの形勢行用。side="sente"=手番側視点=ユーザー視点なので
        // -350 はそのままユーザー側劣勢として表示される。
        cpBefore = -350L,
    )

    /** 本譜モード・開始局面（plyIndex=0） */
    @Test
    fun report_viewer_mainline() {
        captureRoboImage(
            filePath = "src/test/snapshots/report_viewer_mainline.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    ReportScreen(
                        game = sampleGame(),
                        reports = listOf(sampleBlunder()),
                        flip = false,
                        strengthDisplayText = "52 ±27",
                        onBack = {},
                    )
                }
            }
        }
    }

    /** 本譜モード・後手視点（flip=true） */
    @Test
    fun report_viewer_mainline_flipped() {
        captureRoboImage(
            filePath = "src/test/snapshots/report_viewer_mainline_flipped.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    ReportScreen(
                        game = sampleGame().copy(userSide = "gote"),
                        reports = listOf(sampleBlunder()),
                        flip = true,
                        strengthDisplayText = null,
                        onBack = {},
                    )
                }
            }
        }
    }

    /** 悪手なし */
    @Test
    fun report_viewer_no_blunders() {
        captureRoboImage(
            filePath = "src/test/snapshots/report_viewer_no_blunders.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    ReportScreen(
                        game = sampleGame(),
                        reports = emptyList(),
                        flip = false,
                        strengthDisplayText = null,
                        onBack = {},
                    )
                }
            }
        }
    }

    /** 評価値表示あり（本譜・開始局面 = ply 0 の position_eval を表示） */
    @Test
    fun report_viewer_with_eval() {
        captureRoboImage(
            filePath = "src/test/snapshots/report_viewer_with_eval.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    ReportScreen(
                        game = sampleGame(),
                        reports = listOf(sampleBlunder()),
                        flip = false,
                        strengthDisplayText = "52 ±27",
                        evalDisplay = "cp",
                        positionEvals = listOf(
                            PositionEvalRow(ply = 0, scoreCp = 120, mateIn = null),
                            PositionEvalRow(ply = 1, scoreCp = -80, mateIn = null),
                        ),
                        onBack = {},
                    )
                }
            }
        }
    }

    /** positionEvals には bestUsi を持たせ、一致率計算で参照される値に近い形にする。 */
    @Test
    fun report_viewer_eval_graph_and_match_rate() {
        val blunder = sampleBlunder().copy(ply = 3L)
        captureRoboImage(
            filePath = "src/test/snapshots/report_viewer_eval_graph_and_match_rate.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    ReportScreen(
                        game = sampleGame(),
                        reports = listOf(blunder),
                        flip = false,
                        strengthDisplayText = "52 ±27",
                        matchRateDisplayText = "あなた62%",
                        positionEvals = listOf(
                            PositionEvalRow(ply = 0, scoreCp = 50, mateIn = null, bestUsi = "7g7f"),
                            PositionEvalRow(ply = 1, scoreCp = -30, mateIn = null, bestUsi = "8c8d"),
                            PositionEvalRow(ply = 2, scoreCp = 180, mateIn = null, bestUsi = "2g2f"),
                            // 悪手（ply=3）直後の下落点＝グラフ上の朱マーカー位置
                            PositionEvalRow(ply = 3, scoreCp = -620, mateIn = null, bestUsi = "8b3b"),
                            // 詰み絡み（クランプ下限に張り付く）
                            PositionEvalRow(ply = 4, scoreCp = null, mateIn = -7, bestUsi = "2f2e"),
                        ),
                        // 現在手ライン（グラフ）とナビ行の同期を確認するため、
                        // マーカー位置（ply=3）とは別の位置（ply=2）に置く。
                        initialPlyIndex = 2,
                        onBack = {},
                    )
                }
            }
        }
    }

    /**
     * 後手ユーザーでのグラフ符号反転確認。score_cp は先手視点保存で ply=0 が -400
     * （先手劣勢＝後手/自分優勢）のため、自分視点に正規化すると +400 でゼロ基準線の
     * 上側に描かれる（「上=自分有利」になっていることを確認する）。
     */
    @Test
    fun report_viewer_eval_graph_flipped_gote() {
        captureRoboImage(
            filePath = "src/test/snapshots/report_viewer_eval_graph_flipped_gote.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    ReportScreen(
                        game = sampleGame().copy(userSide = "gote"),
                        reports = emptyList(),
                        flip = true,
                        strengthDisplayText = null,
                        positionEvals = listOf(
                            PositionEvalRow(ply = 0, scoreCp = -400, mateIn = null),
                            PositionEvalRow(ply = 1, scoreCp = -600, mateIn = null),
                        ),
                        onBack = {},
                    )
                }
            }
        }
    }

    /** 最善の変化タブは選択中の悪手が無いため無効表示になる。 */
    @Test
    fun report_viewer_list_no_selection() {
        captureRoboImage(
            filePath = "src/test/snapshots/report_viewer_list_no_selection.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    ReportScreen(
                        game = sampleGame(),
                        reports = listOf(sampleBlunder()),
                        flip = false,
                        strengthDisplayText = "52 ±27",
                        onBack = {},
                        initialBodyModeList = true,
                    )
                }
            }
        }
    }

    /** 最善の変化タブ・ライン末尾（ナビラベルに形勢サフィックス、▶ボタンが「▶+」primary色に変わる） */
    @Test
    fun report_viewer_best_pv_end() {
        val blunder = sampleBlunder()
        // bestPv = "2f6f 2d2e"（2手）なので initialPlyIndex=2 でライン末尾を再現する。
        val bestPvMoveCount = blunder.bestPv!!.split(" ").filter { it.isNotBlank() }.size
        captureRoboImage(
            filePath = "src/test/snapshots/report_viewer_best_pv_end.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    ReportScreen(
                        game = sampleGame(),
                        reports = listOf(blunder),
                        flip = false,
                        strengthDisplayText = "52 ±27",
                        onBack = {},
                        initialSelectedIndex = 0,
                        initialViewerModeBestPv = true,
                        initialPlyIndex = bestPvMoveCount,
                    )
                }
            }
        }
    }

    /** 最善の変化タブ・中間局面（形勢行のみ表示、末尾ヒントは出ない） */
    @Test
    fun report_viewer_best_pv_mid() {
        val blunder = sampleBlunder()
        captureRoboImage(
            filePath = "src/test/snapshots/report_viewer_best_pv_mid.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    ReportScreen(
                        game = sampleGame(),
                        reports = listOf(blunder),
                        flip = false,
                        strengthDisplayText = "52 ±27",
                        onBack = {},
                        initialSelectedIndex = 0,
                        initialViewerModeBestPv = true,
                        initialPlyIndex = 1,
                    )
                }
            }
        }
    }

    /** 最善の変化タブ・詰み絡み cp_before（|cp| >= 29_000 は生数字でなく「詰み」表示）。
     *  ライン末尾はヒント表示に置き換わるため、中間局面（initialPlyIndex=1）で形勢行を写す。 */
    @Test
    fun report_viewer_best_pv_mate() {
        // side="sente"=userSide → userCp = 30_000 >= 29_000 → 「この変化の形勢 詰み」（紺青）
        val blunder = sampleBlunder().copy(cpBefore = 30_000L)
        captureRoboImage(
            filePath = "src/test/snapshots/report_viewer_best_pv_mate.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    ReportScreen(
                        game = sampleGame(),
                        reports = listOf(blunder),
                        flip = false,
                        strengthDisplayText = "52 ±27",
                        onBack = {},
                        initialSelectedIndex = 0,
                        initialViewerModeBestPv = true,
                        initialPlyIndex = 1,
                    )
                }
            }
        }
    }

    /** 指し手一覧シートに評価値/勝率表示あり */
    @Test
    fun report_viewer_move_list_with_eval() {
        captureRoboImage(
            filePath = "src/test/snapshots/report_viewer_move_list_with_eval.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    MoveListSheet(
                        moves = sampleGame().movesUsi,
                        currentPly = 2,
                        positionEvals = listOf(
                            PositionEvalRow(ply = 1, scoreCp = 80, mateIn = null),
                            PositionEvalRow(ply = 2, scoreCp = -50, mateIn = null),
                            PositionEvalRow(ply = 3, scoreCp = 150, mateIn = null),
                            PositionEvalRow(ply = 4, scoreCp = -30, mateIn = null),
                        ),
                        evalDisplay = "cp",
                        userIsGote = false,
                        onSelectPly = {},
                    )
                }
            }
        }
    }

    // ═══ レポート画面の検討モード ══════════════════════════════════════════════
    //
    // エンジンは起動せず、表示状態（StudyState）を直接パラメータ注入する
    // （既存の initialSelectedIndex 等の VRT 用パラメータと同じ踏襲パターン）。
    //
    // sampleBlunder().sfenBefore のランク6（"P1P4RP"）には file7=先手歩がいる
    // （USI "7f"）。1マス前進先の file7,rank5（"5gpp1" の空マス帯）は空きマス
    // （USI "7e"）なので、selectedFrom=7f・legalDestinations={7e}・moves=["7f7e"] は
    // 実際に整合した一連の状態になる。

    /** 検討モード・選択マス＋合法手ドット表示（着手前・検討ナビラベルはヒント文言）。 */
    @Test
    fun report_viewer_study_selection() {
        val blunder = sampleBlunder()
        captureRoboImage(
            filePath = "src/test/snapshots/report_viewer_study_selection.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    ReportScreen(
                        game = sampleGame(),
                        reports = listOf(blunder),
                        flip = false,
                        strengthDisplayText = "52 ±27",
                        onBack = {},
                        studyState = dev.miyado.shogisupplement.ui.report.StudyState(
                            baseSfen = blunder.sfenBefore,
                            moves = emptyList(),
                            originIsBestPv = false,
                            originPlyIndex = 40,
                            originSelectedIdx = null,
                            originAbsolutePly = 40,
                            flip = false,
                            selectedFrom = ShogiSquare(7, 6),
                            legalDestinations = setOf(ShogiSquare(7, 5)),
                        ),
                    )
                }
            }
        }
    }

    /** 検討モード・1手指した後の評価値表示（検討ナビラベルのサフィックスに評価値、紺青=優勢）。 */
    @Test
    fun report_viewer_study_eval() {
        val blunder = sampleBlunder()
        captureRoboImage(
            filePath = "src/test/snapshots/report_viewer_study_eval.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    ReportScreen(
                        game = sampleGame(),
                        reports = listOf(blunder),
                        flip = false,
                        strengthDisplayText = "52 ±27",
                        onBack = {},
                        studyState = dev.miyado.shogisupplement.ui.report.StudyState(
                            baseSfen = blunder.sfenBefore,
                            moves = listOf("7f7e"),
                            originIsBestPv = false,
                            originPlyIndex = 40,
                            originSelectedIdx = null,
                            originAbsolutePly = 40,
                            flip = false,
                            evalState = dev.miyado.shogisupplement.ui.report.StudyEvalState.Value(
                                PositionEvalDisplay.EvalLabel(text = "+120", sign = 1),
                            ),
                        ),
                    )
                }
            }
        }
    }

    /** 検討中バナー（最善の変化タブ起点）＋解析中スピナー。タブ切替不可・ghost「終了」。 */
    @Test
    fun report_viewer_study_banner() {
        val blunder = sampleBlunder()
        captureRoboImage(
            filePath = "src/test/snapshots/report_viewer_study_banner.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    ReportScreen(
                        game = sampleGame(),
                        reports = listOf(blunder),
                        flip = false,
                        strengthDisplayText = "52 ±27",
                        onBack = {},
                        studyState = dev.miyado.shogisupplement.ui.report.StudyState(
                            baseSfen = blunder.sfenBefore,
                            moves = listOf("7f7e"),
                            originIsBestPv = true,
                            originPlyIndex = 1,
                            originSelectedIdx = 0,
                            originAbsolutePly = 44,
                            flip = false,
                            evalState = dev.miyado.shogisupplement.ui.report.StudyEvalState.Loading,
                        ),
                    )
                }
            }
        }
    }

    // ═══ レポート画面トップバー圧縮 ════════════════════════════════════════════

    /**
     * 棋戦名（source_place）ありの実運用に近いケース。トップバーは常に2行
     * （タイトル＋対局者）で、ファイル名の3行目は表示しない。
     */
    @Test
    fun report_viewer_source_place() {
        captureRoboImage(
            filePath = "src/test/snapshots/report_viewer_source_place.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    ReportScreen(
                        // source_place は正規化コード（AppStrings.sourcePlaceLabel で表示文言に変換される）。
                        game = sampleGame().copy(sourcePlace = "wars"),
                        reports = listOf(sampleBlunder()),
                        flip = false,
                        strengthDisplayText = "52 ±27",
                        onBack = {},
                    )
                }
            }
        }
    }

    // 対局情報ダイアログの VRT は AlertDialog が別ウィンドウに描画されるため、
    // captureScreenRoboImage を使う ReportGameInfoDialogScreenshotTest に分離
    // （AccountDeleteDialogScreenshotTest と同じ規約）。
}
