package dev.miyado.shogisupplement.ui.report

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.db.BlunderRecord

@Composable
internal fun ReportSummaryBody(
    evalGraphPoints: List<EvalGraphPoint>,
    maxPly: Int,
    blunderPlies: Set<Int>,
    currentPly: Int,
    onPlyTapped: (Int) -> Unit,
    onPlyDragged: (Int) -> Unit,
    reports: List<BlunderRecord>,
    noBlundersMessage: String,
    strengthDisplayText: String?,
    matchRateDisplayText: String?,
    blunderRateDisplayText: String?,
    onViewList: () -> Unit,
    analysisPending: Boolean = false,
    onAnalyze: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // 未解析では空のグラフを無効表示し、解析済みの空データは表示しない。
        if (evalGraphPoints.isNotEmpty() || analysisPending) {
            EvalGraphCard(
                points = evalGraphPoints,
                maxPly = maxPly,
                blunderPlies = blunderPlies,
                currentPly = currentPly,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                onPlyTapped = onPlyTapped,
                onPlyDragged = onPlyDragged,
                enabled = !analysisPending,
            )
        }
        if (analysisPending) {
            PendingAnalysisCard(
                onAnalyze = onAnalyze,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        } else {
            BlunderSummaryCard(
                reports = reports,
                noBlundersMessage = noBlundersMessage,
                strengthDisplayText = strengthDisplayText,
                matchRateDisplayText = matchRateDisplayText,
                blunderRateDisplayText = blunderRateDisplayText,
                onViewList = onViewList,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
