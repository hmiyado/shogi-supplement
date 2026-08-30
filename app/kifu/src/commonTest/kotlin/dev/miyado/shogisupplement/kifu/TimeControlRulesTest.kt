package dev.miyado.shogisupplement.kifu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimeControlRulesTest {

    @Test
    fun `棋桜のショート・フィッシャーはラベル付きで表示される`() {
        assertEquals("ショート" to "3分切れ負け", resolveTimeControlDisplay("kiou", "3分切れ負け", null))
        assertEquals("フィッシャー" to "5分+5秒追加", resolveTimeControlDisplay("kiou", "5分+5秒追加", null))
    }

    @Test
    fun `棋桜のカジュアルと真剣勝負は同じ持ち時間で区別できないためラベルを付けない`() {
        assertEquals(null to "10分+30秒", resolveTimeControlDisplay("kiou", "10分+30秒", null))
    }

    @Test
    fun `lishogiのN分+M秒は秒読みとして表示される`() {
        assertEquals(null to "10分秒読み30秒", resolveTimeControlDisplay("lishogi", "10分+30秒", null))
        assertEquals(null to "15分秒読み60秒", resolveTimeControlDisplay("lishogi", "15分+60秒", null))
    }

    @Test
    fun `切れ負けはサービスに関わらず原文のまま表示される`() {
        assertEquals(null to "3分切れ負け", resolveTimeControlDisplay("lishogi", "3分切れ負け", null))
        assertEquals(null to "10分切れ負け", resolveTimeControlDisplay("wars", "10分切れ負け", null))
        assertEquals(null to "2分切れ負け", resolveTimeControlDisplay("shogi_quest", "2分切れ負け", null))
    }

    @Test
    fun `基本時間0分と秒読みヘッダの組み合わせは1手N秒と表示される`() {
        assertEquals(null to "1手10秒", resolveTimeControlDisplay("wars", "0分", "10秒"))
        assertEquals(null to "1手10秒", resolveTimeControlDisplay("lishogi", "0分", "10秒"))
    }

    @Test
    fun `テーブルに無い組み合わせは原文をそのまま表示する`() {
        assertEquals(null to "5分+30秒", resolveTimeControlDisplay("wars", "5分+30秒", null))
        assertEquals(null to "5分+30秒", resolveTimeControlDisplay(null, "5分+30秒", null))
    }

    @Test
    fun `秒読み値がN秒形式でなければ1手N秒への変換をせず原文のまま表示する`() {
        assertEquals(null to "0分", resolveTimeControlDisplay("wars", "0分", ""))
        assertEquals(null to "0分", resolveTimeControlDisplay("wars", "0分", "不明"))
    }

    @Test
    fun `KifParserが除去しないコロン直後の空白があっても判定・表示できる`() {
        assertEquals("ショート" to "3分切れ負け", resolveTimeControlDisplay("kiou", " 3分切れ負け", null))
        assertEquals(null to "10分秒読み30秒", resolveTimeControlDisplay("lishogi", " 10分+30秒 ", null))
        assertEquals(null to "1手10秒", resolveTimeControlDisplay("wars", " 0分", " 10秒"))
    }

    @Test
    fun `表示文字列はラベルがあれば括弧付きで合成する`() {
        assertEquals("ショート（3分切れ負け）", timeControlDisplayText("kiou", "3分切れ負け", null))
        assertEquals("10分+30秒", timeControlDisplayText("kiou", "10分+30秒", null))
        assertEquals("10分秒読み30秒", timeControlDisplayText("lishogi", "10分+30秒", null))
        assertEquals("1手30秒", timeControlDisplayText("wars", "0分", "30秒"))
    }

    @Test
    fun `持ち時間ヘッダが無ければ表示文字列はnull`() {
        assertNull(timeControlDisplayText("kiou", null, null))
        assertNull(timeControlDisplayText("kiou", "  ", null))
    }

    @Test
    fun `判定表で解決できたかどうかをisKnownTimeControlRuleが返す`() {
        assertTrue(isKnownTimeControlRule("kiou", "3分切れ負け", null))
        assertTrue(isKnownTimeControlRule("lishogi", "10分+30秒", null))
        assertTrue(isKnownTimeControlRule("wars", "0分", "30秒"))
        assertFalse(isKnownTimeControlRule("kiou", "10分+30秒", null))
        assertFalse(isKnownTimeControlRule("wars", "3分切れ負け", null))
        assertFalse(isKnownTimeControlRule(null, "5分+30秒", null))
    }

    @Test
    fun `持ち時間ヘッダが無ければ判定表の対象外`() {
        assertFalse(isKnownTimeControlRule("kiou", null, null))
        assertFalse(isKnownTimeControlRule("kiou", "  ", null))
    }
}
