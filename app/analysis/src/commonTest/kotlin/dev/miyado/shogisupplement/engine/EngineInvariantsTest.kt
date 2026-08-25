package dev.miyado.shogisupplement.engine

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 解析条件が保存済みの解析結果と同じ値のままであることを保証する。
 *
 * ここが変わると過去の解析結果と新しい解析結果が別の条件で出たものになり、
 * 悪手率・棋力推定の比較が成り立たなくなる。値を変えるときはこのテストを
 * 意図して書き換え、既存の解析結果の再解析まで含めて判断する。
 */
class EngineInvariantsTest {

    @Test
    fun 解析条件は保存済みの結果と同じ値のまま() {
        assertEquals(400_000, EngineInvariants.NODES)
        assertEquals(2, EngineInvariants.MULTI_PV)
        assertEquals(1, EngineInvariants.THREADS)
        assertEquals(128, EngineInvariants.USI_HASH_MB)
        assertEquals(20, EngineInvariants.FV_SCALE)
    }
}
