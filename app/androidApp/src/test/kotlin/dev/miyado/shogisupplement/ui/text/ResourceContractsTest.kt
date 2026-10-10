package dev.miyado.shogisupplement.ui.text

import dev.miyado.shogisupplement.ui.generated.resources.*
import dev.miyado.shogisupplement.strength.formatStrengthDecimal
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.getStringArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ResourceContractsTest {
    @Test fun templatesKeepPercentSignsAndArgumentOrder() = runTest {
        assertEquals("5 / 10 局面 (50%)", LocalizedStrings.notifProgress(5, 10, 50))
        assertEquals("25%(1/4)", LocalizedStrings.matchRateValue(25, 1, 4))
        assertEquals("4 局を復元しました（2 局は失敗）。", getString(Res.string.text_game_restore_completed_partial, 4, 2))
        assertEquals("「矢倉」を削除します。同じ局面・形に適用されている他の棋譜からも外れます。", getString(Res.string.position_label_delete_confirmation, "矢倉"))
    }

    @Test fun whitespaceAndNewlinesStayVisible() = runTest {
        assertEquals("先手: ", getString(Res.string.player_sente_prefix))
        assertEquals("  後手: ", getString(Res.string.player_gote_prefix))
        assertTrue(getString(Res.string.home_no_games).contains("\n「棋譜を追加する」"))
    }

    @Test fun strengthRangeKeepsOneDecimalPlace() = runTest {
        assertEquals("推定範囲 44.3–66.1", getString(Res.string.text_strength_detail_range,
            formatStrengthDecimal(44.3125), formatStrengthDecimal(66.125)))
    }

    @Test fun ruleIdsRemainIndependentOfDisplayNames() = runTest {
        assertEquals(listOf("10min", "3min", "10sec"), LocalizedStrings.warsRules().map { it.first })
        assertEquals(listOf("serious", "casual", "fischer", "short"), LocalizedStrings.kiouRules().map { it.first })
        assertEquals("将棋ウォーズ", LocalizedStrings.serviceLabel("shogi_wars"))
        assertEquals("unknown", LocalizedStrings.serviceLabel("unknown"))
        assertEquals(4, getStringArray(Res.array.consent_with_account_points).size)
        assertEquals(1, getStringArray(Res.array.consent_without_account_points).size)
    }
}
