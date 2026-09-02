package dev.miyado.shogisupplement.db

import dev.miyado.shogisupplement.util.currentEpochSeconds

/** ドリル出題・解答履歴の永続化リポジトリのインターフェース。 */
interface DrillRepository {

    /**
     * ドリル出題候補（verdict が ◎ または ○）を優先度順に返す。
     * ◎ → ○ の順、同格内は priority 降順。
     */
    fun getDrillCandidates(): List<BlunderRecord>

    /** 指定棋譜のドリル出題候補だけを優先度順に返す。 */
    fun getDrillCandidatesByGame(gameId: Long): List<BlunderRecord>

    /** 指定IDの悪手レポートを返す。見つからなければ null。 */
    fun getBlunderById(id: Long): BlunderRecord?

    /** ドリル解答を保存する。 @param blunderReportId 出題元レコード。 @param userMoveUsi ユーザーの手。 @param isCorrect 正解か。 @param lossWp 勝率差。 @param attemptedAt 解答時刻。 @param readPv ユーザーが続けて入力した読み筋（USI手列をスペース区切り）。未入力ならnull。 @return 作成されたID。 */
    fun saveDrillAttempt(
        blunderReportId: Long,
        userMoveUsi: String,
        isCorrect: Boolean,
        lossWp: Double?,
        attemptedAt: Long = currentEpochSeconds(),
        readPv: String? = null,
    ): Long

    /** 全blunder_reportの解答回数を、IDから件数へのマップで返す。履歴がないIDは含めない。 */
    fun getDrillAttemptCounts(): Map<Long, Int>

    /** 指定 blunder_report の解答履歴を返す（新しい順）。 */
    fun getDrillAttempts(blunderReportId: Long): List<DrillAttemptRecord>

    /** Supabaseへ未送信の解答を古い順で返す。 */
    fun getDrillAttemptsNotUploaded(limit: Int): List<DrillAttemptRecord>

    /** 解答にクライアント側の冪等キーを保存する。 */
    fun updateDrillAttemptSyncId(id: Long, syncId: String)

    /** 解答のSupabase送信成功時刻を保存する。 */
    fun updateDrillAttemptUploadedAt(id: Long, epochSeconds: Long)

    /** asOfEpochSeconds時点を基準に、直近windowDays暦日のうち解答があった日数（判定境界は深夜0時から4時間の猶予つき）。 */
    fun getDrillAttemptActiveDayCount(windowDays: Int, asOfEpochSeconds: Long = currentEpochSeconds()): Int

    /** 全期間の累計解答数。 */
    fun getDrillAttemptCountTotal(): Int

    /** 連続取組が7日進むごとに1回加算する累計回数（14日連続なら2回）。判定境界は深夜0時から4時間の猶予つき。 */
    fun getDrillAttemptWeekStreakCount(): Int

    /**
     * asOfEpochSeconds時点を基準に、直近windowDays暦日の1日あたり解答数。
     * 古い順にwindowDays件返し、解答が無い日は0で埋める（末尾が当日）。
     */
    fun getDrillAttemptDailyCounts(windowDays: Int, asOfEpochSeconds: Long = currentEpochSeconds()): List<Int>

    /** 正解した解答の累計。 */
    fun getDrillAttemptCorrectCount(): Int

    /** 7日間連続をやり切った回分を達成順に返す。 */
    fun getDrillAttemptWeekStreakDays(): List<WeekStreakDays>
}

/** 7日間連続の達成1回分。日付は "YYYY-MM-DD"。 */
data class WeekStreakDays(val ordinal: Int, val startDay: String, val endDay: String)
