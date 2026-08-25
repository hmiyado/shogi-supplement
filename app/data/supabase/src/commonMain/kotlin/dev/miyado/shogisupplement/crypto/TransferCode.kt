package dev.miyado.shogisupplement.crypto

/**
 * シークレットの人間可読表現＝「引き継ぎコード」（Crockford Base32＋チェックサム）。
 *
 * Why not Crockford公式のmod-37チェックシンボル: 32文字に含まれない記号（`*~$=U`等）を
 * 要求し入力UIが複雑になる。目的は写し間違いの検出だけで暗号強度は要らない。
 */
object TransferCode {

    // Crockford Base32: 0/O・1/I/L・2の桁を混同しやすいO,I,Lを除外した32文字。
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val GROUP_SIZE = 5

    /** [ALPHABET] 32文字でSecretBytes*8ビットを表現するのに要するシンボル数（128/5=25.6→26）。 */
    private const val SECRET_SYMBOLS = 26
    private const val TOTAL_SYMBOLS = SECRET_SYMBOLS + 1 // + checksum 1文字

    /** 2つぶんのシークレット（256bit）を表すのに要するシンボル数（256/5=51.2→52）＋チェックサム。 */
    private const val PAIR_TOTAL_SYMBOLS = 52 + 1

    /** Why not 版の記号を先頭に置く: 長さだけで一意に分かれる。書き写す文字を増やさない。 */
    fun encode(secrets: TransferSecrets): String =
        if (secrets.encSecret.contentEquals(secrets.authSecret)) {
            encode(secrets.encSecret)
        } else {
            val data = secrets.encSecret + secrets.authSecret
            (encodeBase32(data) + checksumSymbol(data)).chunked(GROUP_SIZE).joinToString("-")
        }

    /**
     * Why not 公開する: 引き直した端末の保存値は2つぶんの長さになり、こちらへ渡すと
     * 長さ検査で落ちる。外からは [TransferSecrets] を受ける方だけを使う。
     */
    internal fun encode(secret: ByteArray): String {
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
    fun decode(code: String): ByteArray? = decodeSecrets(code)?.let {
        if (it.encSecret.contentEquals(it.authSecret)) it.encSecret else null
    }

    /** 版に関わらずコードを解釈する。 */
    fun decodeSecrets(code: String): TransferSecrets? {
        val normalized = normalize(code) ?: return null
        return when (normalized.length) {
            TOTAL_SYMBOLS -> decodeFixed(normalized, TRANSFER_SECRET_BYTES)?.let { TransferSecrets(it, it) }
            PAIR_TOTAL_SYMBOLS -> decodeFixed(normalized, TRANSFER_SECRET_BYTES * 2)?.let {
                TransferSecrets(
                    encSecret = it.copyOfRange(0, TRANSFER_SECRET_BYTES),
                    authSecret = it.copyOfRange(TRANSFER_SECRET_BYTES, it.size),
                )
            }
            else -> null
        }
    }

    private fun decodeFixed(normalized: String, expectedBytes: Int): ByteArray? {
        val symbols = normalized.length - 1
        val dataSymbols = normalized.substring(0, symbols)
        val checksum = normalized[symbols]
        val secret = decodeBase32(dataSymbols) ?: return null
        if (secret.size != expectedBytes) return null
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
