package dev.miyado.shogisupplement.db

enum class GameAnalysisStatus(val wireValue: String) {
    PENDING("pending"),
    COMPLETED("completed"),
    ;

    companion object {
        fun fromWireValue(value: String): GameAnalysisStatus =
            entries.firstOrNull { it.wireValue == value } ?: COMPLETED
    }
}

/** ゲームレコードのドメインモデル（UI用）。 */
data class GameRecord(
    val id: Long,
    val fileName: String,
    val contentHash: String,
    val moveCount: Long,
    val senteName: String?,
    val goteName: String?,
    val analyzedAt: Long,
    val rating: Long,
    /** rating推定に使った集計対象手数（自分の手のみ・今局＋過去累計）。フィクスチャ投入等ではnull。 */
    val ratingSampleMoves: Long? = null,
    val coefVersion: String,
    val kifText: String? = null,
    val uploadedAt: Long? = null,
    val movesUsi: List<String> = emptyList(),
    val userSide: String? = null,
    val ratingService: String? = null,
    val ratingRaw: Long? = null,
    val ratingRule: String? = null,
    /**
     * 出典サービスの正規化値（[dev.miyado.shogisupplement.kifu.KifuSource.wireValue]。
     * "wars"/"lishogi"/"kiou"/"other"）。生の「場所」ヘッダ値は含まない
     * （正規化済みの値のみをこの型は保持する）。情報が無ければnull。
     */
    val sourcePlace: String? = null,
    /** 勝者（"sente"/"gote"/null）。 */
    val gameWinner: String? = null,
    /** 終局語（"投了"/"切れ負け"等）。 */
    val endReason: String? = null,
    val analysisStatus: GameAnalysisStatus = GameAnalysisStatus.COMPLETED,
)

/** 悪手レポートのドメインモデル（UI用）。 */
data class BlunderRecord(
    val id: Long,
    val gameId: Long,
    val ply: Long,
    val side: String,
    val moveUsi: String,
    val bestUsi: String?,
    val lossWp: Double,
    val sfenBefore: String,
    val category: String,
    val diffMaterial: Long,
    val punishChecks: Long,
    val tookMovedPiece: Boolean,
    val missedMateIn: Long?,
    val verdict: String,
    val note: String,
    val problemType: String,
    val priority: Double,
    val bestPv: String? = null,
    val punishPv: String? = null,
    /**
     * 悪手前局面の評価値（手番側視点 cp。BlunderJudge.toCp 準拠）。
     * 新規解析分から保存。既存レコードは null（→ cp モードでも勝率表示にフォールバック）。
     */
    val cpBefore: Long? = null,
    /**
     * 悪手後局面の評価値（次手番側視点 cp。BlunderJudge.toCp 準拠）。
     * 損失 cp = cpBefore + cpAfter（cpAfter は相手視点なので加算して手番側の損失量になる）。
     */
    val cpAfter: Long? = null,
    /** 悪手前局面の次善手USI。記録がない場合はnull。 */
    val secondUsi: String? = null,
    /**
     * 悪手前局面の pv2 の評価値（手番側視点 cp。cpBefore と同じ toCp 準拠）。
     */
    val secondCp: Long? = null,
)

/**
 * 局面評価値のドメインモデル（position_eval テーブル対応）。
 *
 * 先手視点に正規化されている（正 = 先手優勢）。
 * - scoreCp: 評価値 cp。詰み局面は null にして mateIn を使う。
 * - mateIn: 詰みまでの手数（正 = 先手が詰ます、負 = 後手が詰ます）。非詰み局面は null。
 */
data class PositionEvalRow(
    val ply: Int,
    val scoreCp: Int?,
    val mateIn: Int?,
    /** pv1（最善手）のUSI表記。正規化はしない。 */
    val bestUsi: String? = null,
    /** pv2（次善手）の評価値 cp。scoreCp と同じ先手視点正規化。 */
    val secondScoreCp: Int? = null,
    /** pv2 の詰み手数。mateIn と同じ先手視点正規化。 */
    val secondMateIn: Int? = null,
    /**
     * pv2（次善手）のUSI表記。bestUsi と同じく正規化しない。
     * 列追加前に保存された旧レコードは null（一致率計算では best_usi のみで判定される）。
     */
    val secondUsi: String? = null,
)
