package dev.miyado.shogisupplement.rating

import dev.miyado.shogisupplement.text.AppStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeclaredRankForGameTest {

    private val ranks = mapOf(
        "shogi_wars" to mapOf(
            "10min" to ShogiRank.Dan(1).toRaw(),
            "3min" to ShogiRank.Kyu(2).toRaw(),
            "10sec" to ShogiRank.Kyu(5).toRaw(),
        ),
        "kiou" to mapOf(
            "short" to ShogiRank.Kyu(3).toRaw(),
            "fischer" to ShogiRank.Dan(2).toRaw(),
        ),
    )

    private fun rankFor(service: String?, source: String?, main: String?, byoyomi: String? = null) =
        declaredRankForGame(service, ranks, source, main, byoyomi)

    @Test
    fun `将棋ウォーズは持ち時間からルールを決めて段級位を引く`() {
        assertEquals(DeclaredRank("10min", ShogiRank.Dan(1).toRaw()), rankFor("shogi_wars", "wars", "10分切れ負け"))
        assertEquals(DeclaredRank("3min", ShogiRank.Kyu(2).toRaw()), rankFor("shogi_wars", "wars", "3分切れ負け"))
    }

    @Test
    fun `10秒将棋は基本時間0分と秒読み10秒の組み合わせで判定する`() {
        assertEquals(DeclaredRank("10sec", ShogiRank.Kyu(5).toRaw()), rankFor("shogi_wars", "wars", "0分", "10秒"))
        assertNull(rankFor("shogi_wars", "wars", "0分", "30秒"), "10秒以外の秒読みはウォーズのルールに無い")
    }

    @Test
    fun `棋桜は短時間とフィッシャーだけ引ける`() {
        assertEquals(DeclaredRank("short", ShogiRank.Kyu(3).toRaw()), rankFor("kiou", "kiou", "3分切れ負け"))
        assertEquals(DeclaredRank("fischer", ShogiRank.Dan(2).toRaw()), rankFor("kiou", "kiou", "5分+5秒追加"))
    }

    @Test
    fun `棋桜の10分プラス30秒は真剣とカジュアルを区別できないため引かない`() {
        assertNull(rankFor("kiou", "kiou", "10分+30秒"))
    }

    @Test
    fun `出典が申告サービスと違う棋譜には申告値を当てはめない`() {
        assertNull(rankFor("shogi_wars", "lishogi", "10分切れ負け"))
        assertNull(rankFor("shogi_wars", null, "10分切れ負け"))
    }

    @Test
    fun `レーティング制のサービスと未申告はここでは扱わない`() {
        assertNull(rankFor("lishogi", "lishogi", "10分+30秒"))
        assertNull(rankFor(null, "wars", "10分切れ負け"))
    }

    @Test
    fun `そのルールを申告していなければ引かない`() {
        assertNull(declaredRankForGame("shogi_wars", emptyMap(), "wars", "10分切れ負け", null))
        assertNull(
            declaredRankForGame("kiou", mapOf("kiou" to mapOf("fischer" to 1)), "kiou", "3分切れ負け", null),
        )
    }

    @Test
    fun `持ち時間ヘッダが無い棋譜は引かない`() {
        assertNull(rankFor("shogi_wars", "wars", null))
    }

    @Test
    fun `ヘッダ前後の空白があっても判定できる`() {
        assertEquals(DeclaredRank("10sec", ShogiRank.Kyu(5).toRaw()), rankFor("shogi_wars", "wars", " 0分", " 10秒"))
    }

    @Test
    fun `返すルールIDは棋力設定の選択肢に含まれる`() {
        val warsRuleIds = AppStrings.warsRules.map { it.first }
        val kiouRuleIds = AppStrings.kiouRules.map { it.first }
        listOf("10分切れ負け", "3分切れ負け").forEach {
            assertTrue(rankFor("shogi_wars", "wars", it)!!.ruleId in warsRuleIds)
        }
        assertTrue(rankFor("shogi_wars", "wars", "0分", "10秒")!!.ruleId in warsRuleIds)
        listOf("3分切れ負け", "5分+5秒追加").forEach {
            assertTrue(rankFor("kiou", "kiou", it)!!.ruleId in kiouRuleIds)
        }
    }
}
