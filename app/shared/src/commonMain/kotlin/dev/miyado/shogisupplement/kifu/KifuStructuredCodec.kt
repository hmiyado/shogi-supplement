package dev.miyado.shogisupplement.kifu

import dev.miyado.shogisupplement.board.PieceType
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * uploaded_games v2 の出典サービス正規化値。
 *
 * @property wireValue DB の `source` 列・研究データに書き込む値
 */
enum class KifuSource(val wireValue: String) {
    WARS("wars"),
    LISHOGI("lishogi"),
    KIOU("kiou"),
    OTHER("other"),
}

/**
 * uploaded_games v2 の平文フィールド。そのままDB・研究データに書き込める形。
 *
 * @property headers ホワイトリスト適用済みヘッダ（[KifuDecomposer.HEADER_WHITELIST] のキーのみ）
 * @property result 終局理由（[KifuGame.endReason] と同じ語彙。投了・切れ負け等）
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
 * uploaded_games v2 の秘匿フィールド。private_enc に暗号化して格納する内容そのもの
 * （このクラス自体は平文構造体であり、AES-256-GCM等の暗号化は呼び出し側の責務＝範囲外）。
 *
 * @property senteName 先手の対局者名
 * @property goteName 後手の対局者名
 * @property extraHeaders ホワイトリスト外の全ヘッダ（棋戦・「場所」の生値・将来増える未知キー全部）。
 *   [KifuDecomposer] が「知らないキーは自動的にここへ落ちる」実装になっているため、
 *   ホワイトリストへの追加漏れが平文流出に直結する事故クラスが構造的に起こらない
 * @property comments KIFのコメント行（`*`）・しおり行（`&`）を出現順に並べたもの。
 *   どの手に紐づくかという位置情報は持たない（較正・研究に不要な添え物のため、
 *   構造化の対象を「往復してテキストとして残っていること」に絞っている）
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

/** [KifuDecomposer.decompose] の戻り値。 */
data class DecomposedKifu(
    val public: PublicKifuFields,
    val private: PrivateKifuFields,
)

/**
 * [KifuGame] を uploaded_games v2 の平文フィールドと秘匿フィールドに分解する。
 *
 * ホワイトリストの適用はこの層でのみ行う（[KifParser] はキー名非依存の汎用実装のまま変えない。
 * パーサをホワイトリストに縛ると表示・KIF再構成という別用途まで巻き添えになるため）。
 */
object KifuDecomposer {

    /**
     * 平文ヘッダとして残してよいキー（実KIF調査済み・確定）。ここに無いキーは [KifuDecomposer.decompose] が自動的に private 側へ回す。
     */
    val HEADER_WHITELIST: Set<String> = setOf(
        "開始日時", "終了日時", "手合割", "持ち時間", "秒読み", "初期局面", "先手段級", "後手段級",
    )

    // 棋桜（KIOU）エクスポートは「場所」ヘッダを出さない代わりに、ファイル先頭に
    // このコメント行を出力する（実サンプル kiou_game1〜3 / narigin_abbrev_game1 /
    // narikei_abbrev_game1 から逆算。棋桜アプリ自体の仕様書は未参照のため確度は推測）。
    private val KIOU_MARKER = Regex("""^#.*KIF形式.*$""")

    private const val LISHOGI_PLACE_PREFIX = "https://lishogi.org/"
    private const val WARS_PLACE = "将棋ウォーズ"

    /** 分丸め対象のヘッダキー（開始日時・終了日時のみ）。 */
    private val DATETIME_HEADER_KEYS = setOf("開始日時", "終了日時")

    // 「YYYY/MM/DD HH:MM:SS」等、末尾が「時:分:秒」の形をしている値だけを対象にする。
    // グループ1が「時:分」までの部分。日付区切りの流儀（/ や -）には依存しない。
    private val SECONDS_SUFFIX = Regex("""^(.*\d{1,2}:\d{2}):\d{2}$""")

    /**
     * @param rawText パース前のKIF原文。コメント行抽出と出典判定（棋桜マーカー検出）に使う
     *   （[KifuGame] はコメント行・しおり行を保持しないため、それらは rawText から別途拾う）
     * @param game [rawText] を [KifParser] でパース済みの結果
     */
    fun decompose(rawText: String, game: KifuGame): DecomposedKifu {
        val publicHeaders = LinkedHashMap<String, String>()
        val extraHeaders = LinkedHashMap<String, String>()
        for ((key, value) in game.headers) {
            when {
                key in HEADER_WHITELIST -> publicHeaders[key] = normalizeWhitelistedValue(key, value)
                // 先手・後手は private_enc の sente_name/gote_name に別枠で持つため、
                // extraHeaders（「ホワイトリスト外ヘッダ」バケツ）には二重に入れない。
                key == "先手" || key == "後手" -> Unit
                // 棋戦・場所（生値）・未知の新規キーは全部ここに落ちる。
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

    /**
     * ホワイトリストヘッダの値を平文保存前に正規化する。現状は開始日時・終了日時の分丸めのみ。
     */
    private fun normalizeWhitelistedValue(key: String, value: String): String =
        if (key in DATETIME_HEADER_KEYS) roundToMinute(value) else value

    /**
     * 日時ヘッダの秒を切り捨てて分精度にする。「段級位＋秒精度の日時＋出典」の組で
     * 将棋ウォーズ等の公開対局履歴と突合すると対局者を再識別できてしまうため、
     * 分精度に丸めてその突合を無効化する（研究軸＝時間帯×悪手率も分精度で足りる）。
     *
     * Why not 四捨五入: 切り上げで未来側に寄る値が生じうるため切り捨てに統一する
     * （対局長さの精度は最大1分落ちるが、1手ごとの消費秒は move_times が別途持つため実害なし）。
     *
     * 末尾が「時:分:秒」の形をしていない値（秒が無い＝既に分精度・想定外の書式）はそのまま通す。
     * 丸められない値を握り潰す／例外を投げるのではなく、原文をそのまま平文に残す方を選ぶ
     * （日時フォーマットは出典サービスにより揺れうるため）。
     */
    private fun roundToMinute(value: String): String {
        val match = SECONDS_SUFFIX.find(value) ?: return value
        return match.groupValues[1]
    }

    /**
     * 出典サービスの正規化。「場所」ヘッダの生値を最優先で判定する
     * （ウォーズ=固定文字列「将棋ウォーズ」、lishogi=`https://lishogi.org/{id}` 形式のURL。
     * 設計書より。lishogi判定は research/scripts/collect_lishogi.py の抽出正規表現が根拠で、
     * 実ファイルでの確認は未了）。「場所」ヘッダを出さない棋桜はファイル先頭のコメント行で判定する。
     * どちらにも当たらなければ other。
     */
    private fun classifySource(rawText: String, place: String?): KifuSource {
        val trimmedPlace = place?.trim()
        return when {
            trimmedPlace == WARS_PLACE -> KifuSource.WARS
            trimmedPlace != null && trimmedPlace.startsWith(LISHOGI_PLACE_PREFIX) -> KifuSource.LISHOGI
            rawText.lineSequence().any { KIOU_MARKER.containsMatchIn(it.trim()) } -> KifuSource.KIOU
            else -> KifuSource.OTHER
        }
    }

    /**
     * コメント行（`*`）・しおり行（`&`）を出現順に抽出する。[KifParser] はこれらを読み捨てて
     * 手順の変換だけを行う汎用実装のため、rawText を別途走査する。変化手順（`変化`）以降は
     * パーサ同様に対象外（本譜のみを構造化データとして保存する設計のため）。
     */
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
 * 構造化フィールド（[PublicKifuFields] + 任意の [PrivateKifuFields]）からKIFテキストを再構成する。
 *
 * private を渡せば元のKIFと同等（対局者名込み）に、渡さなければ実名マスク済みで再構成できる。
 * マスク仕様:
 * ユーザー側の対局者名→"user"・相手側→"opponent"。ユーザーの先後が未確定（[userSide] が null）
 * なら両者とも"player"に置換する。
 */
object KifuReconstructor {

    private const val MOVE_HEADER_LINE = "手数----指手---------消費時間--"

    /**
     * @param public [KifuDecomposer.decompose] で得た平文フィールド
     * @param private 秘匿フィールド。null なら実名マスク済みで再構成する
     * @param userSide ユーザーの先後（"sente"/"gote"/null）。[private] が null のときのみマスクに使う
     *   （private が非null のときは実名を使うため無関係。既存の `side` カラムが正＝設計書）
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
            // 先後未確定: どちらがユーザーか判定できないので両者とも player にする（設計書の例外規定）。
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
        // 「場所」の生値は private 側にしか無い。private が無い（マスク済み）再構成では
        // 出典を語る手段が無くなるが、これは意図どおり（生の場所文字列＝lishogiでは対局を
        // 一意特定できる識別子＝匿名化の対象そのものなので、マスクなし再構成にだけ出す）。
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

    // dev.miyado.shogisupplement.notation.JapaneseNotation.pieceChar と同一のマッピングだが
    // private のため参照できず、用途も違う（連盟式表記の▲/△・曖昧性解消記号は不要。KIFは
    // 移動元 (77) の明示で一意になるため）。ここではKIF再構成専用の最小テーブルとして複製する。
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

    /**
     * 1手をKIFの指し手テキストに変換する。[board] は「この手を指す前」の局面（副作用なし）。
     *
     * 「同」表記は使わず、常に着手先の座標を明示する。KifParser は「同」を直前の着手先の
     * 省略表記として解決するが必須構文ではなく、移動元 (77) または「打」さえあれば
     * 常に一意にパースできるため、直前の着手先を追跡する分岐を増やさずに往復の正しさを保てる。
     */
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
