package dev.miyado.shogisupplement.api

import dev.miyado.shogisupplement.api.analysis.ScoreJson
import dev.miyado.shogisupplement.api.analysis.toJson
import dev.miyado.shogisupplement.api.analysis.toScore
import dev.miyado.shogisupplement.blunder.Score
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class LegacyMateCompatibilityTest {
    @Serializable
    private data class LegacyScoreJson(val type: String, val value: Int)

    private val json = Json { ignoreUnknownKeys = true }
    private fun LegacyScoreJson.toLegacyScore(): Score = when (type) {
        "mate" -> Score.Mate(value)
        else -> Score.Cp(value)
    }

    @Test fun oldClientTreatsUnverifiedReportedWinAsMate() {
        val wire = json.encodeToString(Score.Mate(17, verified = false).toJson())
        assertEquals(Score.Mate(17), json.decodeFromString<LegacyScoreJson>(wire).toLegacyScore())
        assertEquals(Score.Mate(17, verified = false), json.decodeFromString<ScoreJson>(wire).toScore())
    }

    @Test fun oldClientIgnoresVerifiedFieldWithoutDecodeFailure() {
        val wire = json.encodeToString(Score.Mate(3, verified = true).toJson())
        assertEquals(Score.Mate(3), json.decodeFromString<LegacyScoreJson>(wire).toLegacyScore())
        assertEquals(Score.Mate(3), json.decodeFromString<ScoreJson>(wire).toScore())
    }

    @Test fun cpWireIsUnchanged() {
        assertEquals(json.encodeToString(LegacyScoreJson("cp", 30000)), json.encodeToString(Score.Cp(30000).toJson()))
    }
}
