package dev.miyado.shogisupplement.kifu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KifuStructuredCodecTest {

    private val parser = KifParser()

    private fun resource(name: String): String =
        checkNotNull(javaClass.classLoader.getResourceAsStream(name)) { "resource not found: $name" }
            .readBytes().decodeToString()

    private fun decompose(name: String): DecomposedKifu {
        val rawText = resource(name)
        val game = parser.parse(rawText)
        return KifuDecomposer.decompose(rawText, game)
    }

    // 開始日時・終了日時はdecompose時点で分丸めが入るため、実KIFサンプルとの往復比較は
    // 元値そのものではなく丸め後の値を期待値にする（それ以外のホワイトリストキーは元値のまま）。
    private fun expectedHeaderValue(key: String, original: String?): String? {
        if (original == null || key != "開始日時" && key != "終了日時") return original
        return Regex("""^(.*\d{1,2}:\d{2}):\d{2}$""").find(original)?.groupValues?.get(1) ?: original
    }

    // ---- 往復テスト: リポジトリ内の実KIFサンプル全種 ----
    // (app/data/kifu_samples と app/shared/src/jvmTest/resources は重複を除くと10種。
    //  重複分はバイト同一であることを確認済みなので jvmTest/resources 側だけを対象にする)

    private val allSampleFiles = listOf(
        "wars_game1.kif", "wars_game2.kif", "wars_game3.kif",
        "kiou_game1.kif", "kiou_game2.kif", "kiou_game3.kif",
        "narigin_abbrev_game1.kif", "narikei_abbrev_game1.kif",
        "miyado_game1.kif", "miyado_game2.kif",
    )

    @Test
    fun `全実KIFサンプルで パース→分解→再構成→再パース の指し手が完全一致する（private込み）`() {
        for (name in allSampleFiles) {
            val rawText = resource(name)
            val original = parser.parse(rawText)
            val decomposed = KifuDecomposer.decompose(rawText, original)

            val reconstructed = KifuReconstructor.reconstruct(decomposed.public, decomposed.private)
            val reparsed = parser.parse(reconstructed)

            assertEquals(original.moves, reparsed.moves, "$name: 指し手列が一致しない")
            assertEquals(original.timesSeconds, reparsed.timesSeconds, "$name: 消費時間が一致しない")
            assertEquals(original.endReason, reparsed.endReason, "$name: 終局理由が一致しない")
            assertEquals(original.winner, reparsed.winner, "$name: 勝者判定が一致しない")
            // ホワイトリストヘッダは実名込み再構成でも当然保存される。
            // ただし開始日時・終了日時だけは分丸めが入るため元値そのものには戻らない（意図どおり）。
            for (key in KifuDecomposer.HEADER_WHITELIST) {
                assertEquals(expectedHeaderValue(key, original.headers[key]), reparsed.headers[key], "$name: ヘッダ $key が一致しない")
            }
            // 実名込み再構成なので対局者名も一致する
            assertEquals(original.senteName, reparsed.senteName, "$name: 先手名が一致しない")
            assertEquals(original.goteName, reparsed.goteName, "$name: 後手名が一致しない")
        }
    }

    @Test
    fun `private無しの再構成でも指し手とホワイトリストヘッダは保存される`() {
        for (name in allSampleFiles) {
            val rawText = resource(name)
            val original = parser.parse(rawText)
            val decomposed = KifuDecomposer.decompose(rawText, original)

            val masked = KifuReconstructor.reconstruct(decomposed.public, private = null, userSide = "sente")
            val reparsed = parser.parse(masked)

            assertEquals(original.moves, reparsed.moves, "$name: マスク再構成で指し手列が一致しない")
            assertEquals(original.endReason, reparsed.endReason, "$name: マスク再構成で終局理由が一致しない")
            for (key in KifuDecomposer.HEADER_WHITELIST) {
                assertEquals(
                    expectedHeaderValue(key, original.headers[key]),
                    reparsed.headers[key],
                    "$name: マスク再構成でヘッダ $key が一致しない",
                )
            }
        }
    }

    // ---- ホワイトリスト漏れの固定化 ----

    @Test
    fun `棋戦と場所はどのサンプルでも平文headersに出ない`() {
        for (name in allSampleFiles) {
            val decomposed = decompose(name)
            assertFalse("棋戦" in decomposed.public.headers, "$name: 棋戦が平文に混入")
            assertFalse("場所" in decomposed.public.headers, "$name: 場所が平文に混入")
        }
    }

    @Test
    fun `先手名と後手名は平文headersに出ない`() {
        for (name in allSampleFiles) {
            val decomposed = decompose(name)
            assertFalse("先手" in decomposed.public.headers, "$name: 先手が平文に混入")
            assertFalse("後手" in decomposed.public.headers, "$name: 後手が平文に混入")
        }
    }

    @Test
    fun `平文headersはホワイトリストの部分集合でしかない`() {
        for (name in allSampleFiles) {
            val decomposed = decompose(name)
            assertTrue(
                decomposed.public.headers.keys.all { it in KifuDecomposer.HEADER_WHITELIST },
                "$name: ホワイトリスト外キーが平文に混入: ${decomposed.public.headers.keys}",
            )
        }
    }

    @Test
    fun `miyado_game1 - 棋戦ヘッダはprivateのextraHeadersに落ちる`() {
        val decomposed = decompose("miyado_game1.kif")
        assertEquals("Casual Shogi game", decomposed.private.extraHeaders["棋戦"])
        assertEquals("匿名", decomposed.private.senteName)
        assertEquals("匿名", decomposed.private.goteName)
    }

    @Test
    fun `wars_game1 - 場所の生値はprivateのextraHeadersに落ちる`() {
        val decomposed = decompose("wars_game1.kif")
        assertEquals("将棋ウォーズ", decomposed.private.extraHeaders["場所"])
        assertEquals("miyado", decomposed.private.senteName)
        assertEquals("匿名", decomposed.private.goteName)
    }

    @Test
    fun `未知の新規ヘッダキーはprivateのextraHeadersへ自動的に落ちる`() {
        val kif = """
            開始日時：2026/01/01 00:00:00
            手合割：平手
            謎ヘッダ：未知の値
            先手：太郎
            後手：花子
            手数----指手---------消費時間--
               1 ７六歩(77)
        """.trimIndent()
        val game = parser.parse(kif)
        val decomposed = KifuDecomposer.decompose(kif, game)

        assertFalse("謎ヘッダ" in decomposed.public.headers, "未知キーが平文に混入")
        assertEquals("未知の値", decomposed.private.extraHeaders["謎ヘッダ"])
    }

    // ---- 出典判定 ----

    @Test
    fun `wars_game系は場所ヘッダの固定文字列でwarsと判定される`() {
        assertEquals(KifuSource.WARS, decompose("wars_game1.kif").public.source)
        assertEquals(KifuSource.WARS, decompose("wars_game2.kif").public.source)
        assertEquals(KifuSource.WARS, decompose("wars_game3.kif").public.source)
    }

    @Test
    fun `kiou_game系と略記サンプルは先頭コメント行でkiouと判定される`() {
        assertEquals(KifuSource.KIOU, decompose("kiou_game1.kif").public.source)
        assertEquals(KifuSource.KIOU, decompose("kiou_game2.kif").public.source)
        assertEquals(KifuSource.KIOU, decompose("kiou_game3.kif").public.source)
        assertEquals(KifuSource.KIOU, decompose("narigin_abbrev_game1.kif").public.source)
        assertEquals(KifuSource.KIOU, decompose("narikei_abbrev_game1.kif").public.source)
    }

    @Test
    fun `場所ヘッダも棋桜マーカーも無いサンプルはotherと判定される`() {
        // miyado_game1/2 は「場所」ヘッダを持たないため、lishogi URL規約と突き合わせられず
        // other になる（lishogi判定ロジック自体は合成データの下のテストで別途検証する）。
        assertEquals(KifuSource.OTHER, decompose("miyado_game1.kif").public.source)
        assertEquals(KifuSource.OTHER, decompose("miyado_game2.kif").public.source)
    }

    @Test
    fun `場所ヘッダがlishogiのURL形式ならlishogiと判定される`() {
        // lishogi対局URL（場所：https://lishogi.org/{id}）の抽出仕様を
        // 根拠にした合成サンプル。リポジトリ内に実サンプルが無いため合成データで固定する。
        val kif = """
            開始日時：2026/01/01 00:00:00
            場所：https://lishogi.org/abcd1234
            手合割：平手
            先手：太郎
            後手：花子
            手数----指手---------消費時間--
               1 ７六歩(77)
        """.trimIndent()
        val game = parser.parse(kif)
        val decomposed = KifuDecomposer.decompose(kif, game)
        assertEquals(KifuSource.LISHOGI, decomposed.public.source)
        // URLは対局を一意特定できる識別子なのでprivate側のみに残る
        assertEquals("https://lishogi.org/abcd1234", decomposed.private.extraHeaders["場所"])
        assertFalse("場所" in decomposed.public.headers)
    }

    @Test
    fun `棋戦ヘッダがShogi Questならquestと判定される`() {
        val kif = """
            棋戦：Shogi Quest
            手合割：平手
            先手：相手A(464)
            後手：player1(800)
            手数----指手---------消費時間--
               1 ７六歩(77)
        """.trimIndent()
        val game = parser.parse(kif)
        val decomposed = KifuDecomposer.decompose(kif, game)
        assertEquals(KifuSource.QUEST, decomposed.public.source)
        // decompose経由でもレートは分離される（Supabaseアップロード時のprivate_encに
        // レート込みの名前がそのまま入らないようにするため）。
        assertEquals("相手A", decomposed.private.senteName)
        assertEquals("player1", decomposed.private.goteName)
    }

    @Test
    fun `resolvePlayerNamesは先後確認フロー向けにレート抜きの名前だけを返す`() {
        val kif = """
            棋戦：Shogi Quest
            手合割：平手
            先手：相手A(464)
            後手：player1(800)
            手数----指手---------消費時間--
               1 ７六歩(77)
        """.trimIndent()
        val headers = parser.parse(kif).headers
        assertEquals("相手A" to "player1", KifuDecomposer.resolvePlayerNames(kif, headers))
    }

    @Test
    fun `quest判定時のみ対局者名末尾の括弧書きをレートとして分離する`() {
        val kif = """
            棋戦：Shogi Quest
            手合割：平手
            先手：相手A(464)
            後手：player1(800)
            手数----指手---------消費時間--
               1 ７六歩(77)
        """.trimIndent()
        val game = parser.parse(kif)
        val source = KifuDecomposer.classifySource(kif, game.headers["場所"], game.headers["棋戦"])
        val players = KifuDecomposer.resolvePlayers(source, game.headers)
        assertEquals("相手A", players.headers["先手"])
        assertEquals("player1", players.headers["後手"])
        assertEquals(464L, players.senteRating)
        assertEquals(800L, players.goteRating)
    }

    @Test
    fun `questのレートは実名込み再構成で対局者名へ復元されマスク済み再構成では出ない`() {
        val kif = """
            棋戦：Shogi Quest
            手合割：平手
            先手：相手A(464)
            後手：player1(800)
            手数----指手---------消費時間--
               1 ７六歩(77)
        """.trimIndent()
        val game = parser.parse(kif)
        val decomposed = KifuDecomposer.decompose(kif, game)

        val reconstructed = KifuReconstructor.reconstruct(decomposed.public, decomposed.private)
        val reparsed = parser.parse(reconstructed)
        assertEquals("相手A(464)", reparsed.senteName)
        assertEquals("player1(800)", reparsed.goteName)

        val masked = KifuReconstructor.reconstruct(decomposed.public, private = null, userSide = "sente")
        val maskedReparsed = parser.parse(masked)
        assertEquals("user", maskedReparsed.senteName)
        assertEquals("opponent", maskedReparsed.goteName)
    }

    @Test
    fun `quest以外では対局者名の括弧書きをレートとして分離しない`() {
        val kif = """
            場所：将棋ウォーズ
            手合割：平手
            先手：相手A(464)
            後手：花子
            手数----指手---------消費時間--
               1 ７六歩(77)
        """.trimIndent()
        val game = parser.parse(kif)
        val source = KifuDecomposer.classifySource(kif, game.headers["場所"], game.headers["棋戦"])
        assertEquals(KifuSource.WARS, source)
        val players = KifuDecomposer.resolvePlayers(source, game.headers)
        assertEquals("相手A(464)", players.headers["先手"])
        assertNull(players.senteRating)
    }

    @Test
    fun `場所ヘッダが無ければotherになる`() {
        val kif = """
            手合割：平手
            先手：太郎
            後手：花子
            手数----指手---------消費時間--
               1 ７六歩(77)
        """.trimIndent()
        val game = parser.parse(kif)
        val decomposed = KifuDecomposer.decompose(kif, game)
        assertEquals(KifuSource.OTHER, decomposed.public.source)
    }

    // ---- 実名マスク ----

    @Test
    fun `ユーザーが先手ならuser opponentにマスクされる`() {
        val decomposed = decompose("wars_game1.kif")
        val masked = KifuReconstructor.reconstruct(decomposed.public, private = null, userSide = "sente")
        val reparsed = parser.parse(masked)
        assertEquals("user", reparsed.senteName)
        assertEquals("opponent", reparsed.goteName)
    }

    @Test
    fun `ユーザーが後手ならopponent userにマスクされる`() {
        val decomposed = decompose("wars_game1.kif")
        val masked = KifuReconstructor.reconstruct(decomposed.public, private = null, userSide = "gote")
        val reparsed = parser.parse(masked)
        assertEquals("opponent", reparsed.senteName)
        assertEquals("user", reparsed.goteName)
    }

    @Test
    fun `先後未確定なら両者playerにマスクされる`() {
        val decomposed = decompose("wars_game1.kif")
        val masked = KifuReconstructor.reconstruct(decomposed.public, private = null, userSide = null)
        val reparsed = parser.parse(masked)
        assertEquals("player", reparsed.senteName)
        assertEquals("player", reparsed.goteName)
    }

    @Test
    fun `private込みの再構成では実名がそのまま復元される`() {
        val decomposed = decompose("wars_game1.kif")
        val restored = KifuReconstructor.reconstruct(decomposed.public, decomposed.private)
        val reparsed = parser.parse(restored)
        assertEquals("miyado", reparsed.senteName)
        assertEquals("匿名", reparsed.goteName)
    }

    // ---- コメント行 / しおり行 ----

    @Test
    fun `コメント行としおり行はprivateに出現順で保持される`() {
        val kif = """
            手合割：平手
            先手：太郎
            後手：花子
            手数----指手---------消費時間--
               1 ７六歩(77)
            *この手は定跡
               2 ３四歩(33)
            &しおり1
        """.trimIndent()
        val game = parser.parse(kif)
        val decomposed = KifuDecomposer.decompose(kif, game)
        assertEquals(listOf("*この手は定跡", "&しおり1"), decomposed.private.comments)
        // KifParser自体はコメントを読み捨てて指し手だけ返す（既存の汎用実装を変えていない確認）
        assertEquals(listOf("7g7f", "3c3d"), game.moves)
    }

    @Test
    fun `変化手順のコメントは本譜同様に対象外`() {
        val kif = """
            手合割：平手
            手数----指手---------消費時間--
               1 ７六歩(77)
               2 ３四歩(33)

            変化：2手
               2 ２六歩(27)
            *変化中のコメント
        """.trimIndent()
        val game = parser.parse(kif)
        val decomposed = KifuDecomposer.decompose(kif, game)
        assertTrue(decomposed.private.comments.isEmpty())
    }

    // ---- private_enc JSON往復 ----

    @Test
    fun `PrivateKifuFieldsはJSON往復で完全一致する`() {
        val decomposed = decompose("kiou_game1.kif")
        val json = decomposed.private.toJson()
        val restored = PrivateKifuFields.fromJson(json)
        assertEquals(decomposed.private, restored)
    }

    @Test
    fun `PrivateKifuFieldsのJSONキーはsente_name gote_name extra_headers comments`() {
        val private = PrivateKifuFields(
            senteName = "太郎",
            goteName = "花子",
            extraHeaders = mapOf("棋戦" to "テスト対局"),
            comments = listOf("*コメント"),
        )
        val json = private.toJson()
        assertTrue(json.contains("\"sente_name\""))
        assertTrue(json.contains("\"gote_name\""))
        assertTrue(json.contains("\"extra_headers\""))
        assertTrue(json.contains("\"comments\""))
    }

    // ---- 日時の分丸め ----

    @Test
    fun `開始日時と終了日時は秒が切り捨てられて分精度になる`() {
        val decomposed = decompose("wars_game1.kif")
        assertEquals("2026/07/14 09:52", decomposed.public.headers["開始日時"])
        assertEquals("2026/07/14 09:57", decomposed.public.headers["終了日時"])
    }

    @Test
    fun `日時の丸めは四捨五入ではなく常に切り捨て`() {
        // 59秒は切り上げると次の分になってしまう。未来側に寄る値が生じるのを避けるため
        // 常に切り捨てであることを固定する。
        val kif = """
            開始日時：2026/01/01 00:00:59
            手合割：平手
            先手：太郎
            後手：花子
            手数----指手---------消費時間--
               1 ７六歩(77)
        """.trimIndent()
        val game = parser.parse(kif)
        val decomposed = KifuDecomposer.decompose(kif, game)
        assertEquals("2026/01/01 00:00", decomposed.public.headers["開始日時"])
    }

    @Test
    fun `秒が無い日時はそのまま通る`() {
        val kif = """
            開始日時：2026/01/01 00:00
            手合割：平手
            先手：太郎
            後手：花子
            手数----指手---------消費時間--
               1 ７六歩(77)
        """.trimIndent()
        val game = parser.parse(kif)
        val decomposed = KifuDecomposer.decompose(kif, game)
        assertEquals("2026/01/01 00:00", decomposed.public.headers["開始日時"])
    }

    @Test
    fun `想定外の日時書式は丸めずそのまま通る`() {
        // サービスにより書式が揺れうる前提。「時:分:秒」の形に合致しない値は
        // 丸めを諦めて原文をそのまま平文へ通す（握り潰し・例外は禁止）。
        val kif = """
            開始日時：2026年1月1日
            手合割：平手
            先手：太郎
            後手：花子
            手数----指手---------消費時間--
               1 ７六歩(77)
        """.trimIndent()
        val game = parser.parse(kif)
        val decomposed = KifuDecomposer.decompose(kif, game)
        assertEquals("2026年1月1日", decomposed.public.headers["開始日時"])
    }

    @Test
    fun `秒付きの元の日時は秘匿側のどこにも残らない`() {
        for (name in allSampleFiles) {
            val decomposed = decompose(name)
            val extra = decomposed.private.extraHeaders
            assertFalse("開始日時" in extra, "$name: 開始日時がprivateのextraHeadersに混入")
            assertFalse("終了日時" in extra, "$name: 終了日時がprivateのextraHeadersに混入")
            val comments = decomposed.private.comments.joinToString("\n")
            assertFalse(
                Regex("""\d{1,2}:\d{2}:\d{2}""").containsMatchIn(comments),
                "$name: 秒精度の時刻らしき文字列がprivateのcommentsに混入",
            )
        }
    }

    // ---- result / move_times ----

    @Test
    fun `resultは終局理由がそのまま入る`() {
        assertEquals("投了", decompose("wars_game1.kif").public.result)
        assertNull(decompose("miyado_game1.kif").public.result)
    }

    @Test
    fun `move_timesはtimesSecondsとそのまま同じ`() {
        val original = parser.parse(resource("wars_game1.kif"))
        val decomposed = decompose("wars_game1.kif")
        assertEquals(original.timesSeconds, decomposed.public.moveTimesSeconds)
    }

}
