package dev.miyado.shogisupplement.engine

/**
 * WASM資産（KentoAssetCache.swift）配信元URLのDEBUGオーバーライド入力を正規化する純粋関数。
 *
 * iOSにXCTest等のユニットテスト基盤が無く検証方針がJVMテスト完結（CLAUDE.md参照）のため、
 * 手入力の妥当性判断はここに置きjvmTestで検証できるようにする。
 *
 * http(s)スキーム必須・ホスト空文字禁止という最低限の妥当性だけを見る（TLD要否等は判定しない。
 * ローカル開発サーバー(http://127.0.0.1:PORT/)を主用途として想定するため）。
 */
object KentoSiteBaseUrlPolicy {

    /**
     * 末尾スラッシュの有無を吸収し常に1つ付与した形へ揃える（KentoAssetCache.swiftの
     * appendingPathComponentはどちらでも動くが、保存値の表示を安定させるため）。
     * 不正な入力（空・スキーム無し・ホスト無し）は null。
     */
    fun normalize(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return null
        val afterScheme = trimmed.substringAfter("://")
        if (afterScheme.isBlank()) return null
        return trimmed.trimEnd('/') + "/"
    }
}
