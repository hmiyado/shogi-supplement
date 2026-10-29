package dev.miyado.shogisupplement.api.analysis

import dev.miyado.shogisupplement.blunder.Score
import dev.miyado.shogisupplement.engine.PvInfo

fun Score.toJson(): ScoreJson = when (this) {
    is Score.Cp -> ScoreJson(type = "cp", value = value)
    is Score.Mate -> ScoreJson(type = "mate", value = plies, verified = verified)
}

fun ScoreJson.toScore(): Score = when (type) {
    "mate" -> Score.Mate(value, verified = verified)
    else -> Score.Cp(value)
}

fun PvInfo.toJson(): PvInfoJson = PvInfoJson(multipv = multipv, score = score.toJson(), pv = pv, nodes = nodes)

fun PvInfoJson.toPvInfo(): PvInfo = PvInfo(multipv = multipv, score = score.toScore(), pv = pv, nodes = nodes)
