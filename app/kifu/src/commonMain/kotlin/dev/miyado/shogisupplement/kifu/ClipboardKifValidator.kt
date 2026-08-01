package dev.miyado.shogisupplement.kifu

/**
 * クリップボードから貼り付けられたテキストが有効な KIF かどうかを判定するユーティリティ。
 *
 * 判定基準:
 *   1. 空白のみ → false
 *   2. KifParser でパースして例外が発生 → false（平手以外など）
 *   3. 指し手が 0 件かつ認識できるヘッダ（先手・後手・手合割）がない → false
 *   4. それ以外 → true
 *
 * クリップボード取得自体は UI 層で行うため、ここは純粋関数のみ。
 */
object ClipboardKifValidator {

    /**
     * テキストが有効な KIF 棋譜として扱えるかどうかを返す。
     *
     * @param text クリップボードから取得したテキスト
     * @return true = KIF として扱える / false = 棋譜ではない
     */
    fun isValidKif(text: String): Boolean {
        if (text.isBlank()) return false
        return try {
            val game = KifParser().parse(text)
            game.moves.isNotEmpty() ||
                game.headers.containsKey("先手") ||
                game.headers.containsKey("後手") ||
                game.headers.containsKey("手合割")
        } catch (e: KifuParseException) {
            // 平手以外など KifParser が拒否するケースも「KIF として認識できる」扱いにする
            // （ユーザーには別のエラーを表示する必要があるが、棋譜テキストではある）
            true
        } catch (e: Exception) {
            false
        }
    }
}
