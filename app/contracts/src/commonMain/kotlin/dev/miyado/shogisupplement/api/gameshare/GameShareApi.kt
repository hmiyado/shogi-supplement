package dev.miyado.shogisupplement.api.gameshare

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GameShareBranchJson(
    @SerialName("origin_ply") val originPly: Int,
    val moves: List<String>,
)

/**
 * 元棋譜の個人情報を渡さないよう、名前・コメント・評価・KIFヘッダは許可しない。
 * game_keyは所有者と保存済み元棋譜の対応を検証するcontent hashで、公開JSONには含めない。
 */
@Serializable
data class CreateGameShareRequest(
    val version: Int = 1,
    @SerialName("game_key") val gameKey: String,
    @SerialName("initial_sfen") val initialSfen: String,
    val mainline: List<String>,
    @SerialName("selected_ply") val selectedPly: Int,
    val branch: GameShareBranchJson? = null,
    @SerialName("user_side") val userSide: String,
    val flip: Boolean = false,
    val winner: String? = null,
    @SerialName("end_reason") val endReason: String? = null,
)

/** 所有者IDや元ファイル名を公開しないため、再生用の情報だけを持つ。 */
@Serializable
data class GameShareSnapshotJson(
    val id: String,
    val version: Int,
    @SerialName("initial_sfen") val initialSfen: String,
    val mainline: List<String>,
    @SerialName("selected_ply") val selectedPly: Int,
    val branch: GameShareBranchJson? = null,
    @SerialName("user_side") val userSide: String,
    val flip: Boolean = false,
    val winner: String? = null,
    @SerialName("end_reason") val endReason: String? = null,
)
