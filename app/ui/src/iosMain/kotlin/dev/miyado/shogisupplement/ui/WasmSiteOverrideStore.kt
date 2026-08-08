package dev.miyado.shogisupplement.ui

import dev.miyado.shogisupplement.engine.KentoSiteBaseUrlPolicy
import dev.miyado.shogisupplement.engine.KentoSiteOverride
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSUserDefaults

/**
 * WASMバイナリ配信元URLのDEBUGオーバーライドを読み書き・表示する窓口。
 *
 * 永続化先はNSUserDefaults.standardUserDefaults（[KentoSiteOverride.defaultsKey]）。
 * ネットワーク挙動への反映はここでは行わない——実際の配信元決定はダウンロード実装側の
 * 責務で、ここは同じ永続領域を読み書きするだけ（Why not ここで決定まで持つ:
 * Releaseビルドでは保存値を一切読まない担保をダウンロード実装側のコンパイル時分岐で
 * 行っており、決定ロジックを複製するとその担保の外に第二の判定経路ができてしまう）。
 */
internal object WasmSiteOverrideStore {

    enum class Source { ENVIRONMENT, SAVED, PRODUCTION }

    data class EffectiveInfo(val url: String, val source: Source)

    /** 保存済みの生値（未保存なら null）。 */
    fun savedValue(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(KentoSiteOverride.defaultsKey)?.takeIf { it.isNotBlank() }

    /**
     * [input] を正規化して保存する。不正なURL（スキーム無し等。詳細は
     * [KentoSiteBaseUrlPolicy]）なら保存せず false を返す。
     */
    fun save(input: String): Boolean {
        val normalized = KentoSiteBaseUrlPolicy.normalize(input) ?: return false
        NSUserDefaults.standardUserDefaults.setObject(normalized, forKey = KentoSiteOverride.defaultsKey)
        return true
    }

    /** 保存値をクリアする（次回の配信元決定から本番/環境変数の優先順位に戻る）。 */
    fun clear() {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(KentoSiteOverride.defaultsKey)
    }

    /**
     * 現在有効な配信元URLとその由来（環境変数＞保存値＞本番。表示専用——
     * 実際の決定はダウンロード実装側で行われる）。
     */
    fun effectiveInfo(): EffectiveInfo {
        val envOverride = (NSProcessInfo.processInfo.environment[KentoSiteOverride.environmentKey] as? String)?.takeIf { it.isNotBlank() }
        if (envOverride != null) {
            return EffectiveInfo(envOverride, Source.ENVIRONMENT)
        }
        val saved = savedValue()
        if (saved != null) {
            return EffectiveInfo(saved, Source.SAVED)
        }
        return EffectiveInfo(KentoSiteOverride.productionUrl, Source.PRODUCTION)
    }
}
