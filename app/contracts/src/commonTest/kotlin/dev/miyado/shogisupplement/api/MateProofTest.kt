package dev.miyado.shogisupplement.api

import dev.miyado.shogisupplement.api.analysis.ScoreJson
import dev.miyado.shogisupplement.api.analysis.toJson
import dev.miyado.shogisupplement.api.analysis.toScore
import dev.miyado.shogisupplement.blunder.Score
import kotlin.test.Test
import kotlin.test.assertEquals

class MateProofTest {
    @Test fun oldServerMateIsUnconfirmed() {
        assertEquals(Score.Mate(7, verified = false), ScoreJson("mate", 7).toScore())
        assertEquals(Score.Mate(-7, verified = false), ScoreJson("mate", -7).toScore())
        assertEquals(Score.Mate(0, verified = false), ScoreJson("mate", 0).toScore())
    }
    @Test fun verifiedMateSurvivesWireRoundtrip() {
        assertEquals(Score.Mate(3), Score.Mate(3).toJson().toScore())
    }
}
