package dev.miyado.shogisupplement.ui.report

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.blunder.DisplayWinProb
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.classify.BlunderCategoryLabels
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.notation.JapaneseNotation
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.common.formatFixed1
import dev.miyado.shogisupplement.ui.theme.TextStyleData
import dev.miyado.shogisupplement.ui.theme.TextStyleDataMove
import dev.miyado.shogisupplement.ui.theme.shogiColors
import kotlin.math.abs

/**
 * 非負の Double を小数点以下0桁で四捨五入して文字列化する（BlunderCard の勝率%表示用）。
 *
 * "%.0f".format(x)（java.lang.String.format）は Kotlin/Native commonMain では使えないため、
 * multiplatform-safe な実装にしている。呼び出し元（勝率パーセント）は常に非負のため
 * 符号は扱わない。
 */
private fun formatFixed0(value: Double): String = kotlin.math.round(value).toLong().toString()

/** 悪手カード（ミニ盤なし・テキスト情報のみ）。 */
@Composable
fun BlunderCard(
    report: BlunderRecord,
    isSelected: Boolean = false,
    /** 形勢の表示単位（"cp" or "wp"）。 */
    evalDisplay: String = "cp",
    onClick: () -> Unit = {},
) {
    val categoryLabel = BlunderCategoryLabels.of(report.category)
    val shogiColors = MaterialTheme.shogiColors

    // 指し手: 和式表記
    val moveDisplay = remember(report.sfenBefore, report.moveUsi) {
        runCatching {
            JapaneseNotation.format(report.moveUsi, ShogiBoard.fromSfen(report.sfenBefore))
        }.getOrElse { report.moveUsi }
    }
    // 最善手: 和式表記（getOrElse は明示的に usiStr を返すことで String? 型を確定）
    val bestDisplay: String? = remember(report.sfenBefore, report.bestUsi) {
        report.bestUsi?.let { usiStr ->
            runCatching {
                JapaneseNotation.format(usiStr, ShogiBoard.fromSfen(report.sfenBefore))
            }.getOrElse { usiStr }
        }
    }

    // 損失表示（"変化前 → 変化後（差分）"形式 or フォールバック）
    //
    // cp_before/cp_after の意味（cpBefore = 手番側視点、cpAfter = 次手番側視点）:
    //   変化前 (手番側 = 悪手を指した側) = cpBefore
    //   変化後 (手番側換算)              = -cpAfter  ← 符号反転で同一視点に揃える
    //   損失量                           = cpBefore + cpAfter（= 表示上の差分マイナス）
    //
    // 詰み判定: |cpBefore| >= 29_000 または |cpAfter| >= 29_000
    //   変化前 cpBefore >= 29_000 → "詰み"（手番側が詰ます側）
    //   変化後 cpAfter  >= 29_000 → "詰まされ"（手番側が詰まされる側）
    data class EvalDisplay(
        val beforeLabel: String?,  // null = フォールバック（差分のみ）
        val afterLabel: String?,
        val lossLabel: String,
    )
    val cpBefore = report.cpBefore?.toInt()
    val cpAfter = report.cpAfter?.toInt()
    val evalState = remember(evalDisplay, cpBefore, cpAfter, report.lossWp) {
        when {
            evalDisplay == "cp" && cpBefore != null && cpAfter != null -> {
                val isMate = abs(cpBefore) >= 29_000 || abs(cpAfter) >= 29_000
                val userAfterCp = -cpAfter
                EvalDisplay(
                    beforeLabel = when {
                        cpBefore >= 29_000 -> AppStrings.BLUNDER_LOSS_MATE
                        cpBefore <= -29_000 -> AppStrings.BLUNDER_AFTER_MATED
                        else -> AppStrings.cpSignedLabel(cpBefore)
                    },
                    afterLabel = when {
                        userAfterCp >= 29_000 -> AppStrings.BLUNDER_LOSS_MATE
                        userAfterCp <= -29_000 -> AppStrings.BLUNDER_AFTER_MATED
                        else -> AppStrings.cpSignedLabel(userAfterCp)
                    },
                    lossLabel = if (isMate) AppStrings.BLUNDER_LOSS_MATE
                                else AppStrings.blunderLossCp(cpBefore + cpAfter),
                )
            }
            evalDisplay == "wp" && cpBefore != null && cpAfter != null -> {
                val beforeWp = DisplayWinProb.winProb(cpBefore)
                val userAfterWp = DisplayWinProb.winProb(-cpAfter)
                val lossWp = DisplayWinProb.lossWp(cpBefore, cpAfter)
                EvalDisplay(
                    beforeLabel = "${formatFixed0(beforeWp * 100)}%",
                    afterLabel = "${formatFixed0(userAfterWp * 100)}%",
                    lossLabel = "−${formatFixed1(lossWp * 100)}%",
                )
            }
            else -> {
                // 旧レコード（cp未保存）: 保存済み loss_wp の差分のみ
                EvalDisplay(
                    beforeLabel = null,
                    afterLabel = null,
                    lossLabel = "−${kotlin.math.round(report.lossWp * 100).toInt()}%",
                )
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        border = if (isSelected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // n手目（Mincho）+ 判定チップ（primary-soft）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    AppStrings.blunderCardPly(report.ply),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // 判定チップ: primary-soft
                Surface(
                    color = shogiColors.primarySoft,
                    shape = MaterialTheme.shapes.extraSmall,
                ) {
                    Text(
                        text = report.verdict,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                // 形勢の変化表示（Mono・変化後と差分は朱色、変化前は中立）
                if (evalState.beforeLabel != null && evalState.afterLabel != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = evalState.beforeLabel,
                            style = TextStyleData,
                            color = shogiColors.ink2,
                        )
                        Text(
                            text = "→",
                            style = MaterialTheme.typography.bodySmall,
                            color = shogiColors.ink2,
                        )
                        Text(
                            text = "${evalState.afterLabel}（${evalState.lossLabel}）",
                            style = TextStyleData,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                } else {
                    // フォールバック: 差分のみ
                    Text(
                        text = evalState.lossLabel,
                        style = TextStyleData,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // 実戦手（loss色Mono）/ 最善手（primary色Mono）
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    AppStrings.BLUNDER_CARD_ACTUAL,
                    style = MaterialTheme.typography.labelSmall,
                    color = shogiColors.ink3,
                )
                Text(
                    moveDisplay,
                    style = TextStyleDataMove,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (bestDisplay != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        AppStrings.BLUNDER_CARD_BEST,
                        style = MaterialTheme.typography.labelSmall,
                        color = shogiColors.ink3,
                    )
                    Text(
                        bestDisplay,
                        style = TextStyleDataMove,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // 分類チップ（loss-soft）
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = shogiColors.lossSoft,
                    shape = MaterialTheme.shapes.extraSmall,
                ) {
                    Text(
                        text = categoryLabel.label,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // note: line色の左罫線のみ（DESIGN.md。四辺枠にしない）
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(shogiColors.line),
                )
                Text(
                    report.note,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = shogiColors.ink2,
                )
            }
        }
    }
}

/** タブボタン（本譜 / 最善の変化）。 */
@Composable
internal fun ReportViewerTab(
    label: String,
    isActive: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val containerColor = if (isActive) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isActive) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}
