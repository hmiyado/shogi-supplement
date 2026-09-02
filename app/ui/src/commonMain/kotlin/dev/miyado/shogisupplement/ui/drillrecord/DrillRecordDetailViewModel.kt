package dev.miyado.shogisupplement.ui.drillrecord

import dev.miyado.shogisupplement.db.DrillRepository
import dev.miyado.shogisupplement.ui.common.defaultIoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** 学習の記録が見る窓の日数。 */
const val DRILL_RECORD_WINDOW_DAYS = 30

/** 学習の記録の詳細画面の表示データ。[totalAttempts] は1以上（0件なら画面自体を出さない）。 */
data class DrillRecordDetailData(
    val activeDaysInWindow: Int,
    val windowDays: Int,
    val totalAttempts: Int,
    val correctAttempts: Int,
    /** 直近[windowDays]日の1日あたり解答数。古い順で、末尾が当日。 */
    val dailyCounts: List<Int>,
    /** 7日間連続の達成。新しい順。 */
    val weekStreaks: List<WeekStreakRow>,
)

/** 7日間連続の達成1回分。日付は "M/d" へ整形済み。 */
data class WeekStreakRow(val ordinal: Int, val startLabel: String, val endLabel: String)

/**
 * 学習の記録の詳細画面のロードを担う。
 *
 * ホーム画面のカードと同じ集計（取組日数・累計解答数・7日間連続）を使うため、
 * 概要の数字はカードと必ず一致する。
 */
class DrillRecordDetailViewModel(
    private val drillRepository: DrillRepository,
    private val ioDispatcher: CoroutineDispatcher = defaultIoDispatcher,
) {

    /** @return 一度も解いていなければ null（カード自体が出ない状態と揃える）。 */
    suspend fun loadDrillRecordDetail(): DrillRecordDetailData? = withContext(ioDispatcher) {
        val total = drillRepository.getDrillAttemptCountTotal()
        if (total == 0) return@withContext null
        DrillRecordDetailData(
            activeDaysInWindow = drillRepository.getDrillAttemptActiveDayCount(DRILL_RECORD_WINDOW_DAYS),
            windowDays = DRILL_RECORD_WINDOW_DAYS,
            totalAttempts = total,
            correctAttempts = drillRepository.getDrillAttemptCorrectCount(),
            dailyCounts = drillRepository.getDrillAttemptDailyCounts(DRILL_RECORD_WINDOW_DAYS),
            weekStreaks = drillRepository.getDrillAttemptWeekStreakDays()
                .map { WeekStreakRow(it.ordinal, monthDayLabel(it.startDay), monthDayLabel(it.endDay)) }
                .reversed(),
        )
    }
}

/** "YYYY-MM-DD" を "M/d" にする。ゼロ埋めを落として棋譜一覧の日付表記に揃える。 */
internal fun monthDayLabel(isoDate: String): String {
    val parts = isoDate.split("-")
    if (parts.size != 3) return isoDate
    return "${parts[1].trimStart('0')}/${parts[2].trimStart('0')}"
}
