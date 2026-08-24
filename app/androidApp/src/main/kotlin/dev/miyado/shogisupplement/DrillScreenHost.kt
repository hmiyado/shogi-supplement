package dev.miyado.shogisupplement

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.Context
import dev.miyado.shogisupplement.db.AppDatabase
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.drill.DrillJudge
import dev.miyado.shogisupplement.drill.EngineDrillSecondaryJudge
import dev.miyado.shogisupplement.engine.Engine
import dev.miyado.shogisupplement.engine.UsiEngineProcess
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.common.ShogiThinTopBar
import dev.miyado.shogisupplement.ui.drill.DrillQuestionContent
import dev.miyado.shogisupplement.ui.drill.DrillResultContent
import dev.miyado.shogisupplement.ui.drill.DrillUiState
import dev.miyado.shogisupplement.ui.drill.DrillViewModel
import java.io.File

// 共通の表示とViewModelは:uiに置き、Android専用のエンジンfactoryだけをここで組み立てる。
// LocalContextを必要とするトップのDrillScreenはandroidAppに残す。

/** Androidの二次判定。判定ごとに端末Engineを起動し、2局面を解析してから終了する。 */
private fun androidJudgeWithEngine(context: Context): suspend (BlunderRecord, String) -> DrillJudge.DrillResult =
    { blunder, userMoveUsi ->
        try {
            val appContext = context.applicationContext
            val appInfo = appContext.applicationInfo
            val evalDir = File(appContext.filesDir, "eval")
            val engine = UsiEngineProcess.create(appInfo, evalDir)
            try {
                EngineDrillSecondaryJudge { sfen -> engine.analyzeSfen(sfen) }.judge(blunder, userMoveUsi)
            } finally {
                engine.quit()
            }
        } catch (e: Exception) {
            // エンジン起動失敗: 不正解として返す
            DrillJudge.DrillResult(
                isCorrect = false,
                lossWp = Double.NaN,
                userMoveUsi = userMoveUsi,
                bestMoveUsi = blunder.bestUsi,
                reason = DrillJudge.Reason.ENGINE_EVAL,
            )
        }
    }

/**
 * Android用 engineFactory 実装。読み筋のオンデマンド延長（結果画面の「最善」タブ）向け。
 * ReportViewModel（MainViewModel.createEngine）と同じく、呼び出しごとに使い捨てプロセスを
 * 生成する（PvExtensionRunner が延長解析後に無条件で quit() を呼ぶため無害）。
 */
private fun androidDrillEngineFactory(context: Context): () -> Engine = {
    val appContext = context.applicationContext
    val appInfo = appContext.applicationInfo
    val evalDir = File(appContext.filesDir, "eval")
    UsiEngineProcess.create(appInfo, evalDir)
}

// ─── ドリルルートComposable ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrillScreen(
    onBack: () -> Unit,
    vm: DrillViewModel = run {
        val context = LocalContext.current
        val app = context.applicationContext as ShogiApp
        viewModel(
            factory = DrillViewModel.factory(
                gameRepository = AppDatabase.gameRepository(context),
                drillRepository = AppDatabase.drillRepository(context),
                settingsRepository = AppDatabase.settingsRepository(context),
                judgeWithEngine = remember(context) { androidJudgeWithEngine(context) },
                engineFactory = remember(context) { androidDrillEngineFactory(context) },
                drillAttemptSync = app.uploadOrchestrator,
            ),
        )
    },
) {
    val state by vm.state.collectAsState()
    val evalDisplay by vm.evalDisplay.collectAsState()
    val pvExtState by vm.pvExtState.collectAsState()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ShogiThinTopBar(title = AppStrings.DRILL_TITLE, onBack = onBack)
            Box(Modifier.fillMaxSize()) {
            when (val s = state) {
                is DrillUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is DrillUiState.NoCandidates -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                AppStrings.DRILL_EMPTY_TITLE,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                AppStrings.DRILL_EMPTY_BODY,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = onBack) { Text(AppStrings.DRILL_BACK_HOME) }
                        }
                    }
                }

                is DrillUiState.Question -> {
                    DrillQuestionContent(
                        state = s,
                        onSquareTapped = vm::onSquareTapped,
                        onHandPieceTapped = vm::onHandPieceTapped,
                        onPromoteDecision = vm::onPromoteDecision,
                        onSurrender = vm::onSurrender,
                    )
                }

                is DrillUiState.Judging -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text(AppStrings.DRILL_JUDGING)
                        }
                    }
                }

                is DrillUiState.Result -> {
                    DrillResultContent(
                        result = s.drillResult,
                        blunder = s.blunder,
                        sfenBefore = s.sfenBefore,
                        flip = s.flip,
                        evalDisplay = evalDisplay,
                        pvExtState = pvExtState,
                        onExtendBestPv = vm::extendBestPv,
                        onNext = vm::loadNextQuestion,
                        onBack = onBack,
                    )
                }
            }
        }
        }
    }
}
