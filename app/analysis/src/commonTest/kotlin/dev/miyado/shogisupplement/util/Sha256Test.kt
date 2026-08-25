package dev.miyado.shogisupplement.util

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 純Kotlin SHA-256実装の既知ベクタ検証。
 * AnalysisOrchestrator の content_hash 重複チェックが既存Android実装
 * （java.security.MessageDigest ベース）と同一の値を返すことの根拠。
 */
class Sha256Test {

    @Test
    fun emptyString() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sha256Hex(""),
        )
    }

    @Test
    fun abc() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Hex("abc"),
        )
    }

    @Test
    fun longerMessage() {
        // NIST FIPS 180-4 の既知ベクタ（2ブロックにまたがるメッセージ）
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            sha256Hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"),
        )
    }

    @Test
    fun japaneseText() {
        // 実運用（KIFテキスト）に近い日本語混じり文字列。UTF-8バイト列に対しても
        // 決定的な値を返すことのみ検証（既知ベクタとの比較ではなく安定性の確認）。
        val h1 = sha256Hex("先手：miyado\n後手：相手\n手合割：平手\n")
        val h2 = sha256Hex("先手：miyado\n後手：相手\n手合割：平手\n")
        assertEquals(h1, h2)
        assertEquals(64, h1.length)
    }
}
