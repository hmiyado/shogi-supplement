package dev.miyado.shogisupplement.kifu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KifVariationBackupTest {
    @Test
    fun mainLineWithoutVariationsStillDecomposes() {
        val raw = "手合割：平手\n1 ７六歩(77)\n*変化：1手 はメモの一部"
        val parts = KifuDecomposer.decompose(raw, KifParser().parse(raw))
        assertEquals(null, parts.private.variationKif)
        assertEquals(listOf("7g7f"), parts.public.movesUsi)
    }

    @Test
    fun variationTextSurvivesJsonRoundTripWithoutEnteringPublicData() {
        val tail = "変化：1手\r\n1 ２六歩(27)\r\n*秘密  \r\n&確認\r\n"
        val raw = "手合割：平手\r\n1 ７六歩(77)\r\n$tail"
        val parts = KifuDecomposer.decompose(raw, KifParser().parse(raw))
        val decoded = PrivateKifuFields.fromJson(parts.private.toJson())
        assertEquals(tail, decoded.variationKif)
        assertEquals(listOf("7g7f"), parts.public.movesUsi)
        assertTrue(KifuReconstructor.reconstruct(parts.public, decoded).endsWith(tail))
        assertTrue(!KifuReconstructor.reconstruct(parts.public, null).contains("秘密"))
    }
}
