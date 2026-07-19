package dev.miyado.shogisupplement

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.miyado.shogisupplement.ui.theme.ShipporiMinchoFamily
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.Context
import dev.miyado.shogisupplement.db.AppDatabase
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.drill.DrillJudge
import dev.miyado.shogisupplement.engine.Engine
import dev.miyado.shogisupplement.engine.UsiEngineProcess
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.drill.DrillQuestionContent
import dev.miyado.shogisupplement.ui.drill.DrillResultContent
import dev.miyado.shogisupplement.ui.drill.DrillUiState
import dev.miyado.shogisupplement.ui.drill.DrillViewModel
import java.io.File

// DrillQuestionContent / DrillResultContent（＋Preview群）は :ui commonMain
// （app/ui/src/commonMain/.../ui/DrillScreen.kt）にある。
//
// DrillViewModel 本体（状態・タップ処理・DB保存ロジック）も :ui commonMain
// （app/ui/src/commonMain/.../ui/DrillViewModel.kt）にあり、android.app.Application に
// 依存せず GameRepository/DrillRepository/SettingsRepository + judgeWithEngine の
// 関数注入で構成している。
//
// 本ファイルに残るトップの DrillScreen Composable は、MainActivity.kt の呼び出し
// `DrillScreen(onBack = { vm.loadHome() })` がデフォルト引数 vm に依存している構造上、
// LocalContext.current（Android専用）を使うファクトリ組み立てが必要なため :ui へは
// 移動できない（ReportScreen のような完全ホイストはできない構造的な差異）。
//
// パッケージは HomeHost.kt / ReportHost.kt / AccountHost.kt / SettingsHost.kt と同じ
// ルートパッケージ（dev.miyado.shogisupplement）に置く。ファイル名は関数名 DrillScreen
// とは異なり DrillScreenHost.kt のままにしている（他の *Host.kt と同じ命名規則。
// 呼び出し側 MainActivity.kt が DrillScreen(...) を呼ぶため関数名は変えない）。

/**
 * Android用 judgeWithEngine 実装。UsiEngineProcess を1回の判定ごとに起動し、
 * analyzeSfen×2（DrillJudge.judgeByEngine内）を行った後 finally で quit する。
 * 起動失敗時は不正解扱いとする。
 */
private fun androidJudgeWithEngine(context: Context): (BlunderRecord, String) -> DrillJudge.DrillResult =
    { blunder, userMoveUsi ->
        try {
            val appContext = context.applicationContext
            val appInfo = appContext.applicationInfo
            val evalDir = File(appContext.filesDir, "eval")
            val engine = UsiEngineProcess.create(appInfo, evalDir)
            try {
                DrillJudge.judge(blunder, userMoveUsi) { sfen ->
                    engine.analyzeSfen(sfen)
                }
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
        viewModel(
            factory = DrillViewModel.factory(
                gameRepository = AppDatabase.gameRepository(context),
                drillRepository = AppDatabase.drillRepository(context),
                settingsRepository = AppDatabase.settingsRepository(context),
                judgeWithEngine = remember(context) { androidJudgeWithEngine(context) },
                engineFactory = remember(context) { androidDrillEngineFactory(context) },
            ),
        )
    },
) {
    val state by vm.state.collectAsState()
    val evalDisplay by vm.evalDisplay.collectAsState()
    val pvExtState by vm.pvExtState.collectAsState()

    // レポート画面と同じ32dpインライン情報行に統一している（TopAppBar 64dpは使わない）。
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = AppStrings.BACK,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = AppStrings.DRILL_TITLE,
                    style = TextStyle(
                        fontFamily = ShipporiMinchoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                    ),
                    maxLines = 1,
                    modifier = Modifier.padding(start = 2.dp),
                )
            }
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
