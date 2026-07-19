package dev.miyado.shogisupplement.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.rating.ShogiRank
import dev.miyado.shogisupplement.text.AppStrings

// ─── レートルール定数（文言の実体は AppStrings に集約）───────────────────────

private val WARS_RULES: List<Pair<String, String>> get() = AppStrings.warsRules
private val KIOU_RULES: List<Pair<String, String>> get() = AppStrings.kiouRules

// ─── 将棋ウォーズ/棋桜 段級位ヘルパー ──────────────────────────────────────

private val WARS_RANK_LABELS: List<String> = run {
    val kyuList = (30 downTo 1).map { "${it}級" }
    val kanjiDan = listOf("初", "二", "三", "四", "五", "六", "七", "八", "九")
    val danList = kanjiDan.map { "${it}段" }
    kyuList + danList
}

/** ピッカーのインデックス（0=30級 … 29=1級, 30=初段 … 38=九段）→ ShogiRank。 */
private fun warsRankFromIndex(index: Int): ShogiRank {
    return if (index < 30) {
        ShogiRank.Kyu(30 - index)
    } else {
        ShogiRank.Dan(index - 29)
    }
}

/** ShogiRank → ピッカーのインデックス。 */
private fun warsRankToIndex(rank: ShogiRank): Int = when (rank) {
    is ShogiRank.Kyu -> 30 - rank.kyu
    is ShogiRank.Dan -> 29 + rank.dan
}

/**
 * 段級位ピッカーの初期表示インデックス。
 * 保存済み rank がある場合はこの既定値より呼び出し元での `?:` 優先が勝つ
 * （既存挙動維持）。
 */
private const val WARS_RANK_DEFAULT_INDEX = 29  // 将棋ウォーズ: 1級
private const val KIOU_RANK_DEFAULT_INDEX = 29  // 棋桜: 1級

/**
 * 棋力設定ダイアログ。
 * UI順: サービス選択 → サービスごとのアカウント名 → ルール別段級位/レート（任意）
 *
 * サービス/ルール/段級位は申告のみ（記録・較正データ収集用）。
 * 相応判定には使わない。アカウント名のみ先後自動選択に使う。
 * アカウント名はサービスごとに保存する（service_account テーブル）。
 * savedServiceAccounts: サービス → アカウント名（service_account テーブルから）
 * savedServiceRanks: サービス → ルール（対局種別）→ rankRaw のネスト Map
 *   （同じサービスでもルールごとに段級位が異なるため）
 */
@Composable
fun RatingSettingsDialog(
    savedService: String = "lishogi",
    savedRatingRaw: Int? = null,
    savedRatingRule: String? = null,
    savedServiceAccounts: Map<String, String> = emptyMap(),
    savedServiceRanks: Map<String, Map<String, Int>> = emptyMap(),
    onConfirm: (service: String?, ratingRaw: Int?, ratingRule: String?, serviceAccountsNew: Map<String, String>, ranks: Map<String, Map<String, Int>>) -> Unit,
    onDismiss: () -> Unit,
) {
    var service by remember { mutableStateOf(savedService) }
    var ratingText by remember { mutableStateOf((savedRatingRaw ?: 1750).toString()) }
    // サービスごとのアカウント名（state-aware map）
    val serviceAccountNames = remember {
        mutableStateMapOf<String, String>().also { map ->
            savedServiceAccounts.forEach { (svc, name) -> map[svc] = name }
        }
    }
    // ルール別 rankIdx（Compose state-aware map で再描画を保証）
    val warsRankIndices = remember {
        mutableStateMapOf<String, Int>().also { map ->
            savedServiceRanks["shogi_wars"]?.forEach { (rule, raw) ->
                val rank = ShogiRank.fromRaw(raw) ?: return@forEach
                map[rule] = warsRankToIndex(rank)
            }
        }
    }
    val kiouRankIndices = remember {
        mutableStateMapOf<String, Int>().also { map ->
            savedServiceRanks["kiou"]?.forEach { (rule, raw) ->
                val rank = ShogiRank.fromRaw(raw) ?: return@forEach
                map[rule] = warsRankToIndex(rank)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppStrings.RATING_DIALOG_TITLE) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // ── 1. サービス選択（任意）────────────────────────────────────
                Text(AppStrings.RATING_FIELD_SERVICE, style = MaterialTheme.typography.labelMedium)
                AppStrings.serviceOptions.forEach { (value, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { service = value },
                    ) {
                        RadioButton(selected = service == value, onClick = { service = value })
                        Text(label)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ── 2. アカウント名（サービスごと、先後自動選択に使用）──────────
                // 現在選択中のサービスのアカウント名を入力する（段級位と同じタブ内）
                val currentAccountName = serviceAccountNames[service] ?: ""
                OutlinedTextField(
                    value = currentAccountName,
                    onValueChange = { serviceAccountNames[service] = it },
                    label = { Text(AppStrings.RATING_FIELD_ACCOUNT_NAME) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                // ── 3. ルール別段級位/レート入力 ────────────────────────────
                when (service) {
                    "lishogi" -> {
                        // lishogi: レーティング数値1行
                        Text(AppStrings.RATING_FIELD_RATING, style = MaterialTheme.typography.labelMedium)
                        OutlinedTextField(
                            value = ratingText,
                            onValueChange = { ratingText = it },
                            label = { Text("レーティング") },
                            singleLine = true,
                        )
                    }
                    "shogi_wars" -> {
                        Text(AppStrings.RATING_FIELD_RANK, style = MaterialTheme.typography.labelMedium)
                        WARS_RULES.forEach { (ruleId, ruleLabel) ->
                            ServiceRuleRankRow(
                                ruleLabel = ruleLabel,
                                currentIdx = warsRankIndices[ruleId] ?: WARS_RANK_DEFAULT_INDEX,
                                onIdxChange = { warsRankIndices[ruleId] = it },
                            )
                        }
                    }
                    "kiou" -> {
                        Text(AppStrings.RATING_FIELD_RANK, style = MaterialTheme.typography.labelMedium)
                        KIOU_RULES.forEach { (ruleId, ruleLabel) ->
                            ServiceRuleRankRow(
                                ruleLabel = ruleLabel,
                                currentIdx = kiouRankIndices[ruleId] ?: KIOU_RANK_DEFAULT_INDEX,
                                onIdxChange = { kiouRankIndices[ruleId] = it },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // ルール別 rankRaw を収集して serviceRanks Map を作る
                val builtServiceRanks = buildMap<String, Map<String, Int>> {
                    val warsMap = warsRankIndices.mapValues { (_, idx) -> warsRankFromIndex(idx).toRaw() }
                    if (warsMap.isNotEmpty()) put("shogi_wars", warsMap)
                    val kiouMap = kiouRankIndices.mapValues { (_, idx) -> warsRankFromIndex(idx).toRaw() }
                    if (kiouMap.isNotEmpty()) put("kiou", kiouMap)
                }
                // lishogi のレートは savedRatingRaw/ratingText で扱う（ルール別ではなく単一値）
                val ratingRaw = when (service) {
                    "shogi_wars", "kiou" -> null // ルール別に保存するため単一値は使わない
                    else -> ratingText.toIntOrNull()
                }
                // 空でないアカウント名だけを保存する
                val builtAccounts = serviceAccountNames
                    .filterValues { it.isNotBlank() }
                    .mapValues { (_, v) -> v.trim() }
                onConfirm(
                    service.ifEmpty { null },
                    ratingRaw,
                    null, // ルール別に保存するため selectedRule は使わない
                    builtAccounts,
                    builtServiceRanks,
                )
            }) {
                Text(AppStrings.SAVE)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(AppStrings.CANCEL)
            }
        },
    )
}

/**
 * ルール別段級位ピッカー行。
 * currentIdx は呼び出し元で「保存済み値 ?: サービス別初期値」を解決してから渡す
 * （WARS_RANK_DEFAULT_INDEX / KIOU_RANK_DEFAULT_INDEX）。ユーザーが操作しない限り
 * onIdxChange は呼ばれないため、未保存のまま確定した場合は従来通り保存されない。
 */
@Composable
private fun ServiceRuleRankRow(
    ruleLabel: String,
    currentIdx: Int,
    onIdxChange: (Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Text(
            ruleLabel,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = { if (currentIdx > 0) onIdxChange(currentIdx - 1) },
            enabled = currentIdx > 0,
        ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "下げる") }
        Text(
            WARS_RANK_LABELS[currentIdx],
            modifier = Modifier.width(56.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(
            onClick = { if (currentIdx < WARS_RANK_LABELS.lastIndex) onIdxChange(currentIdx + 1) },
            enabled = currentIdx < WARS_RANK_LABELS.lastIndex,
        ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "上げる") }
    }
}
