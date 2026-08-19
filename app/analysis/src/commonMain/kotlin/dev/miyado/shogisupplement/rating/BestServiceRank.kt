package dev.miyado.shogisupplement.rating

/** raw は [ShogiRank.toRaw] と同じ符号化（段 = +1..+9、級 = -1..-30）。 */
data class BestServiceRank(val service: String, val ruleId: String, val raw: Int)

/**
 * lishogi はレーティング（数値）であり段級位ではないため比較対象に含めない。
 * raw は目盛り幅（最低級）がサービスごとに違っても直接比較できる符号化のため、
 * このまま大小比較で最高値を選べる。
 */
fun bestServiceRank(serviceRanks: Map<String, Map<String, Int>>): BestServiceRank? {
    var best: BestServiceRank? = null
    for (service in listOf("shogi_wars", "kiou")) {
        val rules = serviceRanks[service] ?: continue
        for ((ruleId, raw) in rules) {
            val current = best
            if (current == null || raw > current.raw) {
                best = BestServiceRank(service, ruleId, raw)
            }
        }
    }
    return best
}
