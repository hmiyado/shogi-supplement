package dev.miyado.shogisupplement.server.worker

import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** クライアントへ返すエラーが、原因の文言を含まず相関IDだけを載せることを保証する。 */
class ErrorResponsesTest {

    private val log = LoggerFactory.getLogger(ErrorResponsesTest::class.java)

    @Test
    fun `原因の例外メッセージは応答に含まれない`() {
        val cause = IllegalStateException("jdbc://internal-host:5432 connection refused")

        val response = maskedError(log, "unhandled exception", cause)

        assertFalse(response.error.contains("internal-host"), "上流由来の文言を含まないはず")
        assertTrue(Regex("""internal error \(ref: [0-9a-f]{8}\)""").matches(response.error))
    }

    @Test
    fun `相関IDは呼び出しごとに変わる`() {
        val first = maskedError(log, "unhandled exception")
        val second = maskedError(log, "unhandled exception")

        assertFalse(first.error == second.error, "1件のエラーを一意に追えるよう毎回異なるはず")
    }
}
