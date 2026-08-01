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
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // 評価値グラフ（手数×評価値の推移。悪手位置に朱マーカー・現在手にライン）。
        // positionEvals が無い（旧解析・保存前）局は非表示——件数ガードはグラフ側
        // （points.isEmpty()）に任せる。
        if (evalGraphPoints.isNotEmpty()) {
            EvalGraphCard(
                points = evalGraphPoints,
                maxPly = maxPly,
                blunderPlies = blunderPlies,
                currentPly = currentPly,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                onPlyTapped = onPlyTapped,
                onPlyDragged = onPlyDragged,
            )
        }
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
