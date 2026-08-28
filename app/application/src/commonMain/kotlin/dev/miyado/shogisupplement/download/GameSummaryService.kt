package dev.miyado.shogisupplement.download

import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.db.GameRecord

/**
 * ローカルDBへ取り込まず、アップロード済み棋譜を一覧・詳細表示用に取得するだけの経路。
 * [GameDownloadService.downloadAndImport] と異なり保存を行わないため、
 * ローカルDBを持たないプラットフォーム（Web等）でも使える。
 */
interface GameSummaryService {
    suspend fun listGames(): GameSummaryOutcome

    /**
     * @param contentHash [GameRecord.contentHash]（一覧のidはこのサービス内だけの合成値のため、
     *   詳細取得のキーには使えない）。
     */
    suspend fun getDetail(contentHash: String): GameDetailOutcome
}

/** [GameSummaryService.listGames] の結果。 */
sealed class GameSummaryOutcome {
    data class Loaded(val games: List<GameRecord>) : GameSummaryOutcome()

    /** 未ログイン（サーバー上の棋譜を取得するユーザーが特定できない）。 */
    data object NotAuthenticated : GameSummaryOutcome()

    /** 端末シークレットS未生成（復号鍵K_encを導出できない）。 */
    data object NoSecret : GameSummaryOutcome()

    data class NetworkError(val message: String) : GameSummaryOutcome()
}

/**
 * 一覧より詳細な1局ぶんのデータ。エンジン解析結果（評価値・読み筋）はサーバーに保存して
 * いないため、[reports] の各要素はそれらのフィールドを持たない
 * （[dev.miyado.shogisupplement.ui.report.ReportScreen] は該当表示を「算出不可」扱いにする）。
 */
data class GameDetail(val game: GameRecord, val reports: List<BlunderRecord>)

/** [GameSummaryService.getDetail] の結果。 */
sealed class GameDetailOutcome {
    data class Loaded(val detail: GameDetail) : GameDetailOutcome()
    data object NotAuthenticated : GameDetailOutcome()
    data object NoSecret : GameDetailOutcome()

    /** 該当する棋譜が見つからない（削除済み・他ユーザーの棋譜等）。 */
    data object NotFound : GameDetailOutcome()

    data class NetworkError(val message: String) : GameDetailOutcome()
}
