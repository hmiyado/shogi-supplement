package dev.miyado.shogisupplement.download

/**
 * エンジン選定はプラットフォーム依存のため、この抽象は解析を行わず、
 * 1局ぶんの取込・保存処理を [importGame] として受け取る。
 */
interface GameDownloadService {

    suspend fun countRemoteGames(): Result<Int>

    /**
     * 1局の取込失敗は他局の処理を止めない（部分失敗を許容し、最後にまとめて件数を返す）。
     * @param onProgress (done, total) の進捗コールバック。doneはスキップ・失敗も含めて処理済み件数。
     * @param importGame 1局ぶんの「KIF再構成済みテキスト→エンジン解析→DB保存」を行うコールバック。
     */
    suspend fun downloadAndImport(
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
        importGame: suspend (ReconstructedGame) -> GameImportOutcome,
    ): GameDownloadOutcome
}

/** [GameDownloadService.downloadAndImport] が1局ぶんの取込コールバックへ渡す再構成済みデータ。 */
data class ReconstructedGame(
    val kifText: String,
    val fileName: String,
    val contentHash: String,
    val userSide: String?,
    val ratingService: String?,
    val ratingRaw: Long?,
    val ratingRule: String?,
    val sourcePlaceOverride: String?,
)

/** 1局ぶんの取込コールバックの結果。 */
data class GameImportOutcome(
    val success: Boolean,
    /** 成功時のローカルgame_id。失敗時はnull。 */
    val gameId: Long? = null,
)

/** [GameDownloadService.downloadAndImport] の結果。 */
sealed class GameDownloadOutcome {
    /**
     * @param total サーバー上の対象棋譜数
     * @param succeeded 取込成功（新規保存 or 既存content_hashによるスキップ）した数
     * @param failed 復号・再構成・保存いずれかで失敗した数
     */
    data class Completed(val total: Int, val succeeded: Int, val failed: Int) : GameDownloadOutcome()

    /** 未ログイン（サーバー上の棋譜を取得するユーザーが特定できない）。 */
    data object NotAuthenticated : GameDownloadOutcome()

    /** 端末シークレットS未生成（復号鍵K_encを導出できない）。通常は引き継ぎ復元直後には起こらない。 */
    data object NoSecret : GameDownloadOutcome()

    data class NetworkError(val message: String) : GameDownloadOutcome()
}
