package dev.miyado.shogisupplement.ui

import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 棋譜ビューア型レポート画面の状態別 VRT。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w400dp-h800dp-xxhdpi",
    application = android.app.Application::class,
)
class ReportViewerScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @OptIn(ExperimentalRoborazziApi::class)
    private val roborazziOptions = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5),
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
    )

    /**
     * Why not ラムダ版 captureRoboImage: Espresso の idle 待ちが、フレームを流し続ける
     * 無限アニメーションでは終わらない。Compose テスト規則はこれを idle 判定から除く。
     */
    private fun captureViaComposeRule(fileName: String, content: @Composable () -> Unit) {
        composeRule.setContent(content)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/snapshots/$fileName.png",
            roborazziOptions = roborazziOptions,
        )
    }

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
        // bestPv は sfenBefore から合法な手順にする。非合法手では JapaneseNotation.format が
        // 例外となり、ナビラベルが生USIへフォールバックする。
        bestPv = "2f6f 8c8d",
        punishPv = "2d2e 2f2e",
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
                        matchRateDisplayText = "62%(31/50)",
                        blunderRateDisplayText = "12%(3/25)",
                        positionEvals = listOf(
                            PositionEvalRow(ply = 0, scoreCp = 50, mateIn = null, bestUsi = "7g7f"),
                            PositionEvalRow(ply = 1, scoreCp = -30, mateIn = null, bestUsi = "8c8d"),
                            PositionEvalRow(ply = 2, scoreCp = 180, mateIn = null, bestUsi = "2g2f"),
                            PositionEvalRow(ply = 3, scoreCp = -620, mateIn = null, bestUsi = "8b3b"),
                            PositionEvalRow(ply = 4, scoreCp = null, mateIn = -7, bestUsi = "2f2e"),
                        ),
                        initialPlyIndex = 2,
                        onBack = {},
                    )
                }
            }
        }
    }

    /** 後手ユーザーへの符号反転後も、グラフ上側が自分有利になることを示す。 */
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

    /** 最善の変化タブ中間局面で、詰み域の cpBefore を「詰み」と表示する。 */
    @Test
    fun report_viewer_best_pv_mate() {
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

    // 選択元・合法手・指し手は sfenBefore と整合させる。不整合だと表示状態として成立しない。

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
                            origin = dev.miyado.shogisupplement.ui.report.StudyOrigin(
                                label = "40手目 ▲３四飛（−320）",
                                userCp = -320,
                            ),
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
                            displayLine = listOf("7f7e"),
                            chipEvalStates = listOf(
                                dev.miyado.shogisupplement.ui.report.StudyEvalState.Value(
                                    PositionEvalDisplay.EvalLabel(text = "+120", sign = 1),
                                    userCp = 120,
                                    bestMoveText = "▲2六歩",
                                ),
                            ),
                            origin = dev.miyado.shogisupplement.ui.report.StudyOrigin(
                                label = "40手目 ▲３四飛（−320）",
                                userCp = -320,
                            ),
                            originIsBestPv = false,
                            originPlyIndex = 40,
                            originSelectedIdx = null,
                            originAbsolutePly = 40,
                            flip = false,
                            branchFlags = listOf(false),
                            evalState = dev.miyado.shogisupplement.ui.report.StudyEvalState.Value(
                                PositionEvalDisplay.EvalLabel(text = "+120", sign = 1),
                                userCp = 120,
                                bestMoveText = "▲2六歩",
                            ),
                        ),
                    )
                }
            }
        }
    }

    /** 分岐チェブロン付きチップと解析中スピナーを同時に示す。 */
    @Test
    fun report_viewer_study_branch() {
        val blunder = sampleBlunder()
        captureViaComposeRule("report_viewer_study_branch") {
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
                            moves = listOf("7f7e", "3c3d"),
                            displayLine = listOf("7f7e", "3c3d"),
                            origin = dev.miyado.shogisupplement.ui.report.StudyOrigin(
                                label = "44手目 △３二玉",
                                userCp = null,
                            ),
                            originIsBestPv = true,
                            originPlyIndex = 1,
                            originSelectedIdx = 0,
                            originAbsolutePly = 44,
                            flip = false,
                            branchFlags = listOf(false, true),
                            evalState = dev.miyado.shogisupplement.ui.report.StudyEvalState.Loading,
                        ),
                    )
                }
            }
        }
    }

    /** 1手戻っても displayLine 上の先の手を淡色で残す。 */
    @Test
    fun report_viewer_study_line_ahead() {
        val blunder = sampleBlunder()
        captureRoboImage(
            filePath = "src/test/snapshots/report_viewer_study_line_ahead.png",
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
                            displayLine = listOf("7f7e", "3c3d"),
                            origin = dev.miyado.shogisupplement.ui.report.StudyOrigin(
                                label = "40手目 ▲３四飛（−320）",
                                userCp = -320,
                            ),
                            originIsBestPv = false,
                            originPlyIndex = 40,
                            originSelectedIdx = null,
                            originAbsolutePly = 40,
                            flip = false,
                            branchFlags = listOf(false, false),
                            evalState = dev.miyado.shogisupplement.ui.report.StudyEvalState.None,
                        ),
                    )
                }
            }
        }
    }

    /** ダークテーマでも highlight 背景の現在手チップを濃墨で表示する。 */
    @Test
    fun report_viewer_study_selected_chip_dark() {
        val blunder = sampleBlunder()
        captureRoboImage(
            filePath = "src/test/snapshots/report_viewer_study_selected_chip_dark.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme(themeMode = "dark") {
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
                            displayLine = listOf("7f7e"),
                            chipEvalStates = listOf(
                                dev.miyado.shogisupplement.ui.report.StudyEvalState.Value(
                                    PositionEvalDisplay.EvalLabel(text = "+120", sign = 1),
                                    userCp = 120,
                                    bestMoveText = "▲2六歩",
                                ),
                            ),
                            origin = dev.miyado.shogisupplement.ui.report.StudyOrigin(
                                label = "40手目 ▲３四飛（−320）",
                                userCp = -320,
                            ),
                            originIsBestPv = false,
                            originPlyIndex = 40,
                            originSelectedIdx = null,
                            originAbsolutePly = 40,
                            flip = false,
                            branchFlags = listOf(false),
                            evalState = dev.miyado.shogisupplement.ui.report.StudyEvalState.Value(
                                PositionEvalDisplay.EvalLabel(text = "+120", sign = 1),
                                userCp = 120,
                                bestMoveText = "▲2六歩",
                            ),
                        ),
                    )
                }
            }
        }
    }

    /** 準備中も56dp枠を保ち、手動ボタンなしのスピナーへ入れ替える。 */
    @Test
    fun report_viewer_study_preparing() {
        val blunder = sampleBlunder()
        captureViaComposeRule("report_viewer_study_preparing") {
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
                            displayLine = listOf("7f7e"),
                            origin = dev.miyado.shogisupplement.ui.report.StudyOrigin(
                                label = "40手目 ▲３四飛（−320）",
                                userCp = -320,
                            ),
                            originIsBestPv = false,
                            originPlyIndex = 40,
                            originSelectedIdx = null,
                            originAbsolutePly = 40,
                            flip = false,
                            branchFlags = listOf(false),
                            evalState = dev.miyado.shogisupplement.ui.report.StudyEvalState.Preparing,
                        ),
                    )
                }
            }
        }
    }

    /** 40手を折り返してカード内だけをスクロールし、パネル外形を保つ。 */
    @Test
    fun report_viewer_study_pv_scroll() {
        val blunder = sampleBlunder()
        // 全手を開始局面から合法にする。不正な手は生USIへフォールバックし、折り返し幅が変わる。
        val playedMoves = listOf(
            "7g7f", "3c3d", "2g2f", "4c4d", "3i4h", "3a4b", "5g5f", "5c5d",
            "4i5h", "4b4c", "5i6h", "8b3b", "6h7h", "5a6b", "9g9f", "6b7b",
            "8g8f", "7b8b",
        )
        val futureMoves = listOf(
            "8h7g", "7a7b", "7i8h", "9c9d", "6g6f", "6c6d", "5h6g", "4a5b",
            "2f2e", "2b3c", "2e2d", "2c2d", "2h2d", "3c2d", "8f8e", "9d9e",
            "9f9e", "9a9e", "8e8d", "8c8d", "P*8e", "8d8e",
        )
        val displayLine = playedMoves + futureMoves
        captureRoboImage(
            filePath = "src/test/snapshots/report_viewer_study_pv_scroll.png",
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
                            baseSfen = dev.miyado.shogisupplement.board.ShogiBoard().toSfen(),
                            moves = playedMoves,
                            displayLine = displayLine,
                            origin = dev.miyado.shogisupplement.ui.report.StudyOrigin(
                                label = "開始局面",
                                userCp = null,
                            ),
                            originIsBestPv = false,
                            originPlyIndex = 0,
                            originSelectedIdx = null,
                            originAbsolutePly = 0,
                            flip = false,
                            branchFlags = displayLine.indices.map { it == 2 || it == 9 },
                            evalState = dev.miyado.shogisupplement.ui.report.StudyEvalState.Value(
                                PositionEvalDisplay.EvalLabel(text = "+820", sign = 1),
                                userCp = 820,
                                bestMoveText = "▲６八玉",
                            ),
                        ),
                    )
                }
            }
        }
    }

    // Why not 兄弟変化ポップのVRT: Popupは別ウィンドウに開き、captureRoboImageで取得できない。

    /** 棋戦名があってもトップバーをタイトルと対局者の2行に保つ。 */
    @Test
    fun report_viewer_source_place() {
        captureRoboImage(
            filePath = "src/test/snapshots/report_viewer_source_place.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    ReportScreen(
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

    // AlertDialogは別ウィンドウに描画されるため、対局情報VRTはcaptureScreenRoboImage側で扱う。

    /** justCompleted=true の遷移直後。ナビ行スロットが完了通知バナーに排他入替される。 */
    @Test
    fun report_viewer_completion_banner() {
        captureRoboImage(
            filePath = "src/test/snapshots/report_viewer_completion_banner.png",
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
                        justCompleted = true,
                    )
                }
            }
        }
    }
}
