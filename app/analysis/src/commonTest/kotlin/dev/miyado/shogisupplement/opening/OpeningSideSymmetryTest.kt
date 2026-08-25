package dev.miyado.shogisupplement.opening

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 手順の例を左右反転して後手に指させ、先手のときと同じ判定になることを確かめる。
 *
 * 手順の例はほとんどが先手視点で書かれているため、これが無いと後手側の判定は
 * 一度も実行されないまま通ってしまう。
 */
class OpeningSideSymmetryTest {

    private fun mirrorSquare(s: String): String = "${10 - (s[0] - '0')}${'a' + (8 - (s[1] - 'a'))}"

    private fun mirrorMove(m: String): String =
        if (m.contains('*')) {
            m.substring(0, 2) + mirrorSquare(m.substring(2))
        } else {
            mirrorSquare(m.substring(0, 2)) + mirrorSquare(m.substring(2, 4)) + m.substring(4)
        }

    // 反転した手順の手番を1手ずらすための捨て手。反転後の手順と升が重なると
    // 「駒がない」で落ちるため、重ならないものを手順ごとに選ぶ。
    private val fillers = listOf("1g1f", "9g9f", "1i1h", "9i9h", "4i5h", "6i5h")

    private fun asGote(moves: List<String>): List<String>? {
        val mirrored = moves.map { mirrorMove(it) }
        val used = mirrored.flatMap { listOf(it.take(2), it.drop(2).take(2)) }.toSet()
        val filler = fillers.firstOrNull { it.take(2) !in used && it.drop(2).take(2) !in used }
        return filler?.let { listOf(it) + mirrored }
    }

    @Test
    fun 手順を反転すると後手側で同じ判定になる() {
        val failures = mutableListOf<String>()
        var checked = 0

        fun check(name: String, samples: List<OpeningSample>, castle: Boolean) {
            samples.forEach { sample ->
                val gote = asGote(sample.usiMoves) ?: return@forEach
                fun hit(r: SideOpening) = if (castle) name in r.achievedCastles else name in r.tags
                val asSente = hit(OpeningClassifier.classify(sample.usiMoves).black)
                val asGoteHit = hit(OpeningClassifier.classify(gote).white)
                checked++
                if (asSente != asGoteHit) {
                    failures += "$name: ${sample.label} 先手=$asSente 反転後手=$asGoteHit"
                }
            }
        }

        CASTLE_DEFS.forEach { check(it.name, it.samples, castle = true) }
        PLACEMENT_STRATEGY_DEFS.forEach { check(it.name, it.samples, castle = false) }
        EVENT_STRATEGY_DEFS.forEach { check(it.name, it.samples, castle = false) }

        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
        assertTrue(checked > 40, "反転して確かめた手順が少なすぎる: $checked")
    }
}
