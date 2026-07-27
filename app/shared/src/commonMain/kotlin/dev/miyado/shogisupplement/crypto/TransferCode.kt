package dev.miyado.shogisupplement.crypto

/**
 * マスターシークレット S（128bit）の人間可読表現＝「引き継ぎコード」
 * （設計書 付録「引き継ぎコードの詳細仕様」: "Crockford Base32＋チェックサム表記"）。
 *
 * 表記例: `S に対する 26 文字の Crockford Base32 + チェックサム1文字を5文字ずつハイフン区切り`
 * （例: `XXXXX-XXXXX-XXXXX-XXXXX-XXXXX-XX`）。
 *
 * Why not Crockfordの公式mod-37チェックシンボル: 公式チェックシンボルは値全体を1個の大きい整数として
 * mod 37 する方式で、37文字目のアルファベット（`*~$=U`等）が32文字の通常アルファベットに
 * 含まれない記号を要求し、入力UIの複雑化を招く。ここでの目的は「桁の写し間違い・入れ替わりの
 * 検出」だけで暗号強度は不要なため、通常のCrockfordアルファベット32文字だけで表現できる
 * 軽量な位置重み付きチェックサム（mod 32）を独自に採用する（設計書に具体アルゴリズムの
 * 指定は無いため、このタスクの判断としてここに記録する）。
 */
object TransferCode {

    // Crockford Base32: 0/O・1/I/L・2の桁を混同しやすいO,I,Lを除外した32文字。
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val GROUP_SIZE = 5

    /** [ALPHABET] 32文字でSecretBytes*8ビットを表現するのに要するシンボル数（128/5=25.6→26）。 */
    private const val SECRET_SYMBOLS = 26
    private const val TOTAL_SYMBOLS = SECRET_SYMBOLS + 1 // + checksum 1文字

    /** [secret] を表示用の引き継ぎコード文字列にエンコードする（5文字ごとにハイフン区切り）。 */
    fun encode(secret: ByteArray): String {
        require(secret.size == TRANSFER_SECRET_BYTES) {
            "secretは${TRANSFER_SECRET_BYTES}バイトである必要があります: ${secret.size}"
        }
        val dataSymbols = encodeBase32(secret)
        check(dataSymbols.length == SECRET_SYMBOLS)
        val all = dataSymbols + checksumSymbol(secret)
        return all.chunked(GROUP_SIZE).joinToString("-")
    }

    /**
     * 引き継ぎコード文字列から S を復元する。ハイフン・空白は無視し、大文字小文字を無視し、
     * Crockfordの紛らわしい文字（O→0, I/L→1）を正規化する。チェックサム不一致・
     * 文字種不正・長さ不正は null を返す（例外を投げない＝入力フォームでそのままエラー表示に使える）。
     */
    fun decode(code: String): ByteArray? {
        val normalized = normalize(code) ?: return null
        if (normalized.length != TOTAL_SYMBOLS) return null
        val dataSymbols = normalized.substring(0, SECRET_SYMBOLS)
        val checksum = normalized[SECRET_SYMBOLS]
        val secret = decodeBase32(dataSymbols) ?: return null
        if (secret.size != TRANSFER_SECRET_BYTES) return null
        return if (checksumSymbol(secret) == checksum) secret else null
    }

    private fun checksumSymbol(secret: ByteArray): Char {
        var acc = 0
        for ((index, b) in secret.withIndex()) {
            acc = (acc + (b.toInt() and 0xFF) * (index + 1)) % ALPHABET.length
        }
        return ALPHABET[acc]
    }

    private fun encodeBase32(data: ByteArray): String {
        val sb = StringBuilder()
        var bitBuffer = 0
        var bitsInBuffer = 0
        for (b in data) {
            bitBuffer = (bitBuffer shl 8) or (b.toInt() and 0xFF)
            bitsInBuffer += 8
            while (bitsInBuffer >= GROUP_SIZE) {
                val index = (bitBuffer shr (bitsInBuffer - GROUP_SIZE)) and 0x1F
                sb.append(ALPHABET[index])
                bitsInBuffer -= GROUP_SIZE
            }
        }
        if (bitsInBuffer > 0) {
            val index = (bitBuffer shl (GROUP_SIZE - bitsInBuffer)) and 0x1F
            sb.append(ALPHABET[index])
        }
        return sb.toString()
    }

    private fun decodeBase32(text: String): ByteArray? {
        val out = ArrayList<Byte>((text.length * GROUP_SIZE) / 8 + 1)
        var bitBuffer = 0
        var bitsInBuffer = 0
        for (c in text) {
            val index = ALPHABET.indexOf(c)
            if (index < 0) return null
            bitBuffer = (bitBuffer shl GROUP_SIZE) or index
            bitsInBuffer += GROUP_SIZE
            if (bitsInBuffer >= 8) {
                val value = (bitBuffer shr (bitsInBuffer - 8)) and 0xFF
                out.add(value.toByte())
                bitsInBuffer -= 8
            }
        }
        // 末尾の余りビットは常にゼロ埋めのはず（エンコード側の仕様）。壊れている場合は破損として弾く。
        val remainderMask = (1 shl bitsInBuffer) - 1
        if (bitsInBuffer > 0 && (bitBuffer and remainderMask) != 0) return null
        return out.toByteArray()
    }

    private fun normalize(code: String): String? {
        val sb = StringBuilder()
        for (raw in code) {
            if (raw == '-' || raw.isWhitespace()) continue
            val upper = raw.uppercaseChar()
            val mapped = when (upper) {
                'O' -> '0'
                'I', 'L' -> '1'
                else -> upper
            }
            if (ALPHABET.indexOf(mapped) < 0) return null
            sb.append(mapped)
        }
        return sb.toString()
    }
}
