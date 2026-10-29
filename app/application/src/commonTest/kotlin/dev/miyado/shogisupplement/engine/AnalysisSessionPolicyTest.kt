package dev.miyado.shogisupplement.engine

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnalysisSessionPolicyTest {

    @Test
    fun `解析中で無進捗時間が閾値以上なら復帰時に再開する`() {
        assertTrue(
            shouldResumeAnalysisAfterForeground(
                isAnalyzing = true,
                lastProgressAtEpochSeconds = 100L,
                nowEpochSeconds = 105L,
                idleThresholdSeconds = 5L,
            ),
        )
    }

    @Test
    fun `解析中でも無進捗時間が閾値未満なら再送しない`() {
        assertFalse(
            shouldResumeAnalysisAfterForeground(
                isAnalyzing = true,
                lastProgressAtEpochSeconds = 100L,
                nowEpochSeconds = 104L,
                idleThresholdSeconds = 5L,
            ),
        )
    }

    @Test
    fun `解析中でないか時刻が無ければ再送しない`() {
        assertFalse(shouldResumeAnalysisAfterForeground(false, 100L, 105L))
        assertFalse(shouldResumeAnalysisAfterForeground(true, null, 105L))
    }
}
