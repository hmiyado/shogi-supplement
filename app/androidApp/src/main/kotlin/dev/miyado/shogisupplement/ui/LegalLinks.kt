package dev.miyado.shogisupplement.ui

/**
 * アプリが参照する法務ページの URL 定数。
 *
 * 規約は GH Pages（https://shogi-supplement.miyado.dev/）でホストされる。
 * リポジトリ作成はリリース準備時のため、現時点では URL が 404 でも構わない。
 */
object LegalLinks {
    /** 利用規約・プライバシーポリシーの公開 URL（GH Pages）。 */
    const val TERMS_URL = "https://shogi-supplement.miyado.dev/terms.html"

    /**
     * フィードバック用 X アカウント URL（DMで報告を受け付ける。#8）。
     * GitHub Issuesでの公開報告から移行した。
     */
    const val FEEDBACK_URL = "https://x.com/shogisupplement"

    /**
     * Webヘルプ（仕組みとデータの詳細説明）の公開 URL（GH Pages: docs/help.html）。
     * 研究データの出典など、アプリのリリースと独立して更新したい説明はこちらに置く。
     */
    const val HELP_WEB_URL = "https://shogi-supplement.miyado.dev/help.html"

    /** 推定棋力カードの「?」から直接開く、Webヘルプの「推定棋力（偏差値）の算出方法」節。 */
    const val HELP_WEB_STRENGTH_URL = "$HELP_WEB_URL#strength"

    // LicenseInfoScreen の表示テキスト（AppStrings.LICENSE_SOURCE_URL）と同じ値。
    // 実際に開くURLはプラットフォーム側のこの定数を使う（iOS の IOS_SOURCE_REPO_URL と同じ複製パターン）。
    /** ライセンス画面の「ソースリポジトリ」リンクから開く GitHub リポジトリ URL。 */
    const val SOURCE_REPO_URL = "https://github.com/hmiyado/shogi-supplement"
}
