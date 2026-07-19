package dev.miyado.shogisupplement.classify

import dev.miyado.shogisupplement.text.AppStrings

/**
 * 悪手カテゴリの表示ラベル。
 *
 * 内部キー（BlunderClassifier が出力する文字列）は変更禁止。
 * UI 表示のみをここで変換する。文言の実体は [AppStrings.categoryDisplay]（1箇所集約）。
 */
data class BlunderCategoryLabel(
    /** UI 表示用ラベル（短い）。 */
    val label: String,
)

/**
 * 内部キー → [BlunderCategoryLabel] のマッピング。
 *
 * 未知キーはキー文字列をそのままラベルに返す（フォールバック）。
 */
object BlunderCategoryLabels {

    private val mapping: Map<String, BlunderCategoryLabel> =
        AppStrings.categoryDisplay.mapValues { (_, v) -> BlunderCategoryLabel(label = v) }

    /** 内部キーから [BlunderCategoryLabel] を返す。未知キーはフォールバック。 */
    fun of(internalKey: String): BlunderCategoryLabel =
        mapping[internalKey] ?: BlunderCategoryLabel(label = internalKey)

    /** 全内部キーの一覧（テスト・列挙用）。 */
    val knownKeys: Set<String> get() = mapping.keys
}
