package dev.miyado.shogisupplement.rating

/** 棋譜のルールに対応する申告段級位。[rankRaw] は [ShogiRank.toRaw] の符号化。 */
data class DeclaredRank(val ruleId: String, val rankRaw: Int)

// 段級位制サービスの設定ID → KIFの出典（KifuSource.wireValue。両者で綴りが違う）。
private val RANK_SERVICE_SOURCES = mapOf("shogi_wars" to "wars", "kiou" to "kiou")

private fun ruleIdFor(service: String, timeControl: String, byoyomi: String?): String? = when (service) {
    "shogi_wars" -> when {
        timeControl == "10分切れ負け" -> "10min"
        timeControl == "3分切れ負け" -> "3min"
        timeControl == "0分" && byoyomi == "10秒" -> "10sec"
        else -> null
    }
    // 棋桜の「10分+30秒」を引かない理由: 真剣とカジュアルの両方に使われ、KIFから区別できない。
    "kiou" -> when (timeControl) {
        "3分切れ負け" -> "short"
        "5分+5秒追加" -> "fischer"
        else -> null
    }
    else -> null
}

/**
 * 申告済みの段級位から、この棋譜のルールに対応する1件を選ぶ。
 * [serviceRanks] は service → ruleId → rankRaw、[sourcePlace] は KifuSource.wireValue。
 * 出典が申告サービスと違う・ルールを判定できない・そのルールが未申告ならnullを返す。
 */
fun declaredRankForGame(
    service: String?,
    serviceRanks: Map<String, Map<String, Int>>,
    sourcePlace: String?,
    timeControlRaw: String?,
    byoyomiRaw: String?,
): DeclaredRank? {
    if (service == null || timeControlRaw == null) return null
    if (RANK_SERVICE_SOURCES[service] != sourcePlace) return null
    val ruleId = ruleIdFor(service, timeControlRaw.trim(), byoyomiRaw?.trim()) ?: return null
    val rankRaw = serviceRanks[service]?.get(ruleId) ?: return null
    return DeclaredRank(ruleId, rankRaw)
}
