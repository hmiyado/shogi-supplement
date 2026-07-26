package dev.miyado.shogisupplement.kifu

import dev.miyado.shogisupplement.board.PieceType
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** @property wireValue DB の `source` 列・研究データに書き込む値 */
enum class KifuSource(val wireValue: String) {
    WARS("wars"),
    LISHOGI("lishogi"),
    KIOU("kiou"),
    OTHER("other"),
}

/**
 * @property headers ホワイトリスト適用済みヘッダ（[KifuDecomposer.HEADER_WHITELIST] のキーのみ）
 * @property source 出典サービスの正規化値（生の「場所」ヘッダは private 側のみに残る）
 */
data class PublicKifuFields(
    val movesUsi: List<String>,
    val moveTimesSeconds: List<Int?>,
    val headers: Map<String, String>,
    val result: String?,
    val source: KifuSource,
)

/**
 * private_enc に暗号化して格納する内容そのもの（このクラス自体は平文構造体であり、
 * 暗号化は呼び出し側の責務＝範囲外）。
 *
 * @property extraHeaders ホワイトリスト外の全ヘッダ。[KifuDecomposer] は知らないキーを
 *   自動的にここへ落とすため、ホワイトリストへの追加漏れが平文流出に直結しない
 * @property comments KIFのコメント行（`*`）・しおり行（`&`）を出現順に並べたもの。
 *   どの手に紐づくかという位置情報は持たない
 */
@Serializable
data class PrivateKifuFields(
    @SerialName("sente_name") val senteName: String?,
    @SerialName("gote_name") val goteName: String?,
    @SerialName("extra_headers") val extraHeaders: Map<String, String>,
    val comments: List<String>,
) {
    /** 暗号化前のJSON表現。付録の `private_enc` ペイロード形式に対応する。 */
    fun toJson(): String = json.encodeToString(this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(text: String): PrivateKifuFields = json.decodeFromString(text)
    }
}

data class DecomposedKifu(
    val public: PublicKifuFields,
    val private: PrivateKifuFields,
)

// Why not [KifParser] にホワイトリストを持たせる: パーサは表示・KIF再構成という
// 別用途にも使う汎用実装のため、ホワイトリスト適用はこの層(decompose)に閉じる。
object KifuDecomposer {

    /** 平文ヘッダとして残してよいキー。ここに無いキーは private 側へ回る。 */
    val HEADER_WHITELIST: Set<String> = setOf(
        "開始日時", "終了日時", "手合割", "持ち時間", "秒読み", "初期局面", "先手段級", "後手段級",
    )

    // 棋桜（KIOU）エクスポートは「場所」ヘッダを出さない代わりに、ファイル先頭にこの
    // コメント行を出力する（実サンプルから逆算した経験則で、棋桜アプリの公式仕様に基づくものではない）。
    private val KIOU_MARKER = Regex("""^#.*KIF形式.*$""")

    private const val LISHOGI_PLACE_PREFIX = "https://lishogi.org/"
    private const val WARS_PLACE = "将棋ウォーズ"

    private val DATETIME_HEADER_KEYS = setOf("開始日時", "終了日時")

    // 末尾が「時:分:秒」の値だけを対象にする。グループ1が「時:分」までの部分。
    private val SECONDS_SUFFIX = Regex("""^(.*\d{1,2}:\d{2}):\d{2}$""")

    /**
     * @param rawText パース前のKIF原文。[KifuGame] はコメント行・しおり行を保持しないため、
     *   それらとKIOUマーカーはrawTextから別途拾う
     * @param game [rawText] を [KifParser] でパース済みの結果
     */
    fun decompose(rawText: String, game: KifuGame): DecomposedKifu {
        val publicHeaders = LinkedHashMap<String, String>()
        val extraHeaders = LinkedHashMap<String, String>()
        for ((key, value) in game.headers) {
            when {
                key in HEADER_WHITELIST -> publicHeaders[key] = normalizeWhitelistedValue(key, value)
                // 先手・後手はprivate_encのsente_name/gote_nameに別枠で持つため、ここでは捨てる
                // （extraHeadersへの二重格納ではない。バグに見えるが意図的）。
                key == "先手" || key == "後手" -> Unit
                else -> extraHeaders[key] = value
            }
        }

        return DecomposedKifu(
            public = PublicKifuFields(
                movesUsi = game.moves,
                moveTimesSeconds = game.timesSeconds,
                headers = publicHeaders,
                result = game.endReason,
                source = classifySource(rawText, game.headers["場所"]),
            ),
            private = PrivateKifuFields(
                senteName = game.senteName,
                goteName = game.goteName,
                extraHeaders = extraHeaders,
                comments = extractComments(rawText),
            ),
        )
    }

    private fun normalizeWhitelistedValue(key: String, value: String): String =
        if (key in DATETIME_HEADER_KEYS) roundToMinute(value) else value

    /**
     * 日時ヘッダの秒を切り捨てて分精度にする。「段級位＋秒精度の日時＋出典」の組で
     * 将棋ウォーズ等の公開対局履歴と突合すると対局者を再識別できてしまうため、分精度に
     * 丸めてその突合を無効化する（プライバシー要件。精度を戻すと再識別可能になる）。
     *
     * Why not 四捨五入: 切り上げで未来側に寄る値が生じうるため切り捨てに統一する。
     *
     * 末尾が「時:分:秒」の形をしていない値はそのまま通す（丸められない値を握り潰す／
     * 例外を投げるのではなく原文を残す。日時フォーマットは出典サービスにより揺れうるため）。
     */
    private fun roundToMinute(value: String): String {
        val match = SECONDS_SUFFIX.find(value) ?: return value
        return match.groupValues[1]
    }

    /**
     * 出典サービスの正規化。lishogi判定（`https://lishogi.org/`プレフィックス）は
     * research/scripts/collect_lishogi.py の抽出正規表現を参考にした値。
     *
     * Why not [decompose] 内部限定にしない: [dev.miyado.shogisupplement.engine.AnalysisOrchestrator]
     * のローカルDB保存経路からも呼ぶため public。判定ロジックを1箇所に集約し、アップロード用の
     * 分解処理と保存経路とで分類基準がずれる事故を防ぐ。
     */
    fun classifySource(rawText: String, place: String?): KifuSource {
        val trimmedPlace = place?.trim()
        return when {
            trimmedPlace == WARS_PLACE -> KifuSource.WARS
            trimmedPlace != null && trimmedPlace.startsWith(LISHOGI_PLACE_PREFIX) -> KifuSource.LISHOGI
            rawText.lineSequence().any { KIOU_MARKER.containsMatchIn(it.trim()) } -> KifuSource.KIOU
            else -> KifuSource.OTHER
        }
    }

    // [KifParser] はコメント行・しおり行を読み捨てるため、rawTextを別途走査する。
    // 変化手順（`変化`）以降は対象外（本譜のみを構造化データとして保存する）。
    private fun extractComments(rawText: String): List<String> {
        val comments = mutableListOf<String>()
        for (rawLine in rawText.lineSequence()) {
            val trimmed = rawLine.trim()
            if (trimmed.startsWith("変化")) break
            if (trimmed.startsWith("*") || trimmed.startsWith("&")) comments.add(trimmed)
        }
        return comments
    }
}

/**
 * 構造化フィールドからKIFテキストを再構成する。private を渡せば元のKIFと同等（対局者名込み）に、
 * 渡さなければ実名マスク済みで再構成できる。マスク仕様: ユーザー側の対局者名→"user"・
 * 相手側→"opponent"。ユーザーの先後が未確定（[userSide] が null）なら両者とも"player"。
 */
object KifuReconstructor {

    private const val MOVE_HEADER_LINE = "手数----指手---------消費時間--"

    /**
     * @param private 秘匿フィールド。null なら実名マスク済みで再構成する
     * @param userSide ユーザーの先後（"sente"/"gote"/null）。[private] が null のときのみマスクに使う
     */
    fun reconstruct(public: PublicKifuFields, private: PrivateKifuFields?, userSide: String? = null): String {
        val (senteName, goteName) = resolveNames(private, userSide)
        val sb = StringBuilder()
        appendHeaders(sb, public.headers, private, senteName, goteName)
        sb.append(MOVE_HEADER_LINE).append('\n')
        appendMoves(sb, public)
        return sb.toString()
    }

    private fun resolveNames(private: PrivateKifuFields?, userSide: String?): Pair<String?, String?> =
        when {
            private != null -> private.senteName to private.goteName
            userSide == "sente" -> "user" to "opponent"
            userSide == "gote" -> "opponent" to "user"
            // 先後未確定: どちらがユーザーか判定できないため両者とも player にする。
            else -> "player" to "player"
        }

    private fun appendHeaders(
        sb: StringBuilder,
        headers: Map<String, String>,
        private: PrivateKifuFields?,
        senteName: String?,
        goteName: String?,
    ) {
        fun line(key: String, value: String?) {
            if (value != null) sb.append(key).append('：').append(value).append('\n')
        }

        val extra = private?.extraHeaders.orEmpty()
        line("開始日時", headers["開始日時"])
        line("終了日時", headers["終了日時"])
        line("棋戦", extra["棋戦"])
        // 「場所」の生値はprivate側にしか無い。マスク済み再構成で出典が消えるのは意図どおり
        // （lishogiでは対局を一意特定できる識別子＝匿名化対象そのものなので、実名再構成にだけ出す）。
        line("場所", extra["場所"])
        line("持ち時間", headers["持ち時間"])
        line("秒読み", headers["秒読み"])
        line("手合割", headers["手合割"])
        line("初期局面", headers["初期局面"])
        line("先手", senteName)
        line("先手段級", headers["先手段級"])
        line("後手", goteName)
        line("後手段級", headers["後手段級"])
        extra.filterKeys { it != "棋戦" && it != "場所" }.forEach { (key, value) -> line(key, value) }
        private?.comments.orEmpty().forEach { sb.append(it).append('\n') }
    }

    private fun appendMoves(sb: StringBuilder, public: PublicKifuFields) {
        val board = ShogiBoard()
        for ((index, usi) in public.movesUsi.withIndex()) {
            val move = ShogiMove.fromUsi(usi)
            val moveText = formatMove(move, board)
            val timeSeconds = public.moveTimesSeconds.getOrNull(index)
            val timeSuffix = if (timeSeconds != null) " (${formatClock(timeSeconds)}/00:00:00)" else ""
            sb.append((index + 1).toString()).append(' ').append(moveText).append(timeSuffix).append('\n')
            board.push(move)
        }
        val result = public.result
        if (result != null) {
            sb.append((public.movesUsi.size + 1).toString()).append(' ').append(result).append('\n')
        }
    }

    private val FILE_CHARS = arrayOf("", "１", "２", "３", "４", "５", "６", "７", "８", "９")
    private val RANK_CHARS = arrayOf("", "一", "二", "三", "四", "五", "六", "七", "八", "九")

    // Why not JapaneseNotation.pieceChar を再利用: privateで参照できない上、KIFは移動元(77)の
    // 明示で一意になるため連盟式表記の▲/△・曖昧性解消記号が不要で用途も違う。ここに複製する。
    private fun pieceChar(type: PieceType): String = when (type) {
        PieceType.PAWN -> "歩"
        PieceType.LANCE -> "香"
        PieceType.KNIGHT -> "桂"
        PieceType.SILVER -> "銀"
        PieceType.GOLD -> "金"
        PieceType.BISHOP -> "角"
        PieceType.ROOK -> "飛"
        PieceType.KING -> "玉"
        PieceType.PROM_PAWN -> "と"
        PieceType.PROM_LANCE -> "成香"
        PieceType.PROM_KNIGHT -> "成桂"
        PieceType.PROM_SILVER -> "成銀"
        PieceType.PROM_BISHOP -> "馬"
        PieceType.PROM_ROOK -> "龍"
    }

    // [board] は「この手を指す前」の局面（副作用なし）。
    // Why not 「同」表記: 移動元(77)または「打」さえあれば一意にパースできるため、
    // 直前の着手先を追跡する分岐を増やさず常に着手先の座標を明示する。
    private fun formatMove(move: ShogiMove, board: ShogiBoard): String {
        val destText = FILE_CHARS[move.to.file] + RANK_CHARS[move.to.rank]
        val dropType = move.dropType
        if (dropType != null) {
            return "$destText${pieceChar(dropType)}打"
        }
        val from = move.from ?: error("非打ち手にfromがありません: $move")
        val movedType = board.pieceAt(from)?.type ?: error("移動元に駒がありません: $from")
        val promoteSuffix = if (move.promote) "成" else ""
        return "$destText${pieceChar(movedType)}$promoteSuffix(${from.file}${from.rank})"
    }

    /** 秒 → "m:ss"。累計時間欄は KifParser が読まないため常に同じ値のダミーで埋める。 */
    private fun formatClock(seconds: Int): String {
        val mm = seconds / 60
        val ss = seconds % 60
        return "$mm:${ss.toString().padStart(2, '0')}"
    }
}
