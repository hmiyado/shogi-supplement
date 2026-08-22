package dev.miyado.shogisupplement.kifu

/** クリップボードのテキストが有効なKIFとして解析できるかを判定する。 */
object ClipboardKifValidator {

    /** テキストが有効なKIFかを返す。 @param text 判定対象のテキスト。 @return KIFとして有効か。 */
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
