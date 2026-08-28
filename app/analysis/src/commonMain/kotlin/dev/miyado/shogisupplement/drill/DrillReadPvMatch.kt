package dev.miyado.shogisupplement.drill

/**
 * ユーザーが入力した読み筋と実際の進行を先頭から突き合わせる。
 *
 * 手順は分岐しうるため、一致長のみを返す（正誤判定には使わない）。
 */
object DrillReadPvMatch {

    /** @param userPv ユーザーが入力した読み筋（予測手自体は含まない、以降の手のみ）。 @param actualPv 実際の進行（予測手自体は含まない、以降の手のみ）。 @return 先頭から一致した手数。 */
    fun matchLength(userPv: List<String>, actualPv: List<String>): Int {
        var matched = 0
        while (matched < userPv.size && matched < actualPv.size && userPv[matched] == actualPv[matched]) {
            matched++
        }
        return matched
    }
}
