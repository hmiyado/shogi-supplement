package dev.miyado.shogisupplement.text

/**
 * ユーザーに見える文言の一元管理。
 *
 * 文言の最終確認・修正はこのファイルの編集だけで済むようにする（miyadoさん指示）。
 * 動的な値はテンプレート関数の引数で受け取る。
 *
 * セクション構成:
 *   1. 悪手カテゴリ（ラベル・一行説明）
 *   2. 相応判定（verdict 表示名・note テンプレート・教材名）
 *   3. ホーム画面
 *   4. 棋力設定ダイアログ・自分の側ダイアログ
 *   5. 解析中画面・通知
 *   6. レポート画面（棋譜ビューア・悪手カード）
 *   7. ドリル画面
 *   8. エラー
 */
object AppStrings {

    // ═══ 1. 悪手カテゴリ（ラベル・一行説明）═════════════════════════════════
    // 内部キー（BlunderClassifier 出力）は変更禁止。表示のみここで変換する。

    /** 内部キー → UI 表示ラベル。 */
    val categoryDisplay: Map<String, String> = mapOf(
        "詰み見逃し" to "詰み逃し",
        "頓死" to "頓死",
        "駒損（即取り）" to "タダ取られ",
        "駒損（タクティクス）" to "手筋による駒損",
        "玉の危険（寄せ）" to "玉の危険",
        "位置的・その他" to "形勢のミス（その他）",
    )

    // ═══ 2. 相応判定（verdict・note・教材名）═════════════════════════════════

    /** verdict 表示名（記号＋名前）。DB に保存されるためリリース後の変更は移行が必要。 */
    const val VERDICT_PRIORITY = "◎ 優先出題"
    const val VERDICT_TARGET = "○ 出題対象"
    const val VERDICT_SKIP = "△ 見送り"

    /** 悪手カテゴリ → 教材名（詰み見逃し以外）。 */
    val problemByCategory: Map<String, String> = mapOf(
        "頓死" to "受け・危険察知の問題",
        "駒損（即取り）" to "駒の利き・紐の確認問題",
        "駒損（タクティクス）" to "手筋 (両取り・素抜き) の問題",
        "玉の危険（寄せ）" to "玉の早逃げ・受けの問題",
        "位置的・その他" to "形勢判断・大局観 (要人力レビュー)",
    )

    /**
     * 詰み見逃しの教材名。n = 実際の見逃し手数（JudgeInput.missedMateIn 直接）。
     * 例: n=5 → "5手で勝ち切る問題"、n=11 → "11手で勝ち切る問題"
     * 係数表バケット（1手/3手/5手/7手+）は内部の rate 引き用のみに使用し、表示には出さない。
     * 「詰将棋」と呼ばない理由: エンジンのmateは受けなしの勝ち確定手数で、連続王手の
     * 詰み筋とは限らない（必至型の実例: wars_game3）。df-pnによる厳密な区別は未実装。
     */
    fun problemMate(n: Int): String = "${n}手で勝ち切る問題"

    /**
     * note: 詰み見逃し（◎/○/△共通。verdictの判定ロジック自体は変えない）。
     * n = 実際の見逃し手数。pct は係数表バケット単位の率なので実手数と厳密には
     * 一致しないが、帯別の目安として近似を許容する。
     */
    fun noteMate(n: Int, pct: String): String =
        "あなたの棋力帯の${n}手詰の詰め逃し率: $pct"

    /**
     * 係数表の帯名（レート表記）→ 表示用の偏差値帯ラベル。
     * 境界は帯端レート（1300/1600/1900/2200）を [StrengthNorm] v1 で換算した値。
     * 係数表の帯名自体を偏差値表記に変えない理由: 帯名は係数表・DB保存noteの
     * 正規化キーとして流通しており、表記だけの問題は表示側の写像で閉じるのが安全。
     */
    val bandDeviationLabels: Map<String, String> = mapOf(
        "<1300" to "偏差値36未満",
        "1300-1599" to "偏差値36-47",
        "1600-1899" to "偏差値47-59",
        "1900-2199" to "偏差値59-71",
        "2200+" to "偏差値71+",
    )

    private fun bandLabel(bandName: String): String =
        bandDeviationLabels[bandName] ?: bandName

    /** note: スイング系の発生頻度（自帯のみ。最上位帯比較は廃止）。 */
    fun noteTwoPoint(bandName: String, gamesPerBand: Int): String =
        "あなたの棋力帯(${bandLabel(bandName)}): 約${gamesPerBand}局に1回"

    /** note: スイング系・△見送り（どの帯でも稀）。noteTwoPoint と同じ形式。 */
    fun noteSkipRare(bandName: String, gamesPerBand: Int): String =
        "あなたの棋力帯(${bandLabel(bandName)}): 約${gamesPerBand}局に1回"

    // ═══ 3. ホーム画面 ═══════════════════════════════════════════════════════

    const val APP_TITLE = "将棋サプリ"
    const val HOME_OPEN_KIF = "棋譜を追加する"
    const val HOME_TODAYS_DRILL_TITLE = "今日の1問"
    fun homeTodaysDrillPly(ply: Long): String = "${ply}手目の局面"
    const val HOME_TODAYS_DRILL_TAP = "タップしてドリルを開始"
    const val HOME_PAST_ANALYSES = "過去の解析"
    const val HOME_NO_GAMES = "まだ解析した棋譜がありません。\n「棋譜を追加する」からファイル（.kif）を選択してください。"

    // 強さ指標カード
    const val STRENGTH_CARD_TITLE = "推定棋力（偏差値）"
    fun strengthDetail(gameCount: Int): String = "直近${gameCount}局から算出"

    // ゲームカード
    fun gameMoveCount(count: Long): String = "${count}手"
    fun playersLine(senteName: String?, goteName: String?): String =
        "先手: ${senteName ?: "不明"}  後手: ${goteName ?: "不明"}"

    // ═══ 4. 棋力設定ダイアログ・自分の側ダイアログ ═══════════════════════════

    const val RATING_DIALOG_TITLE = "棋力設定"
    const val RATING_FIELD_ACCOUNT_NAME = "アカウント名（先後自動選択に使用）"
    const val RATING_FIELD_SERVICE = "将棋サービス（任意）"
    const val RATING_FIELD_RANK = "段級位（任意）"
    const val RATING_FIELD_RATING = "レート"
    const val SAVE = "保存"
    const val CANCEL = "キャンセル"

    /** サービス選択肢（ID to 表示ラベル）。 */
    val serviceOptions: List<Pair<String, String>> = listOf(
        "lishogi" to "lishogi",
        "shogi_wars" to "将棋ウォーズ",
        "kiou" to "棋桜",
    )

    /** 将棋ウォーズのルール選択肢（ID to 表示ラベル）。増減はこのリストだけ修正する。 */
    val warsRules: List<Pair<String, String>> = listOf(
        "10min" to "10分切れ負け",
        "3min" to "3分切れ負け",
        "10sec" to "10秒将棋",
    )

    /**
     * 棋桜のルール選択肢（ID to 表示ラベル）。
     * 要調整: 2026-06リリースの新サービスのため区分は暫定。確認でき次第更新する。
     */
    val kiouRules: List<Pair<String, String>> = listOf(
        "serious" to "真剣",
        "casual" to "カジュアル",
        "fischer" to "フィッシャー",
        "short" to "短時間",
    )

    const val SIDE_DIALOG_TITLE = "自分の側"
    fun sideSente(senteName: String?): String = if (senteName != null) "先手（$senteName）" else "先手"
    fun sideGote(goteName: String?): String = if (goteName != null) "後手（$goteName）" else "後手"
    const val START_ANALYSIS = "解析開始"

    // ═══ 5. 解析中画面・通知 ══════════════════════════════════════════════════

    fun analyzingProgress(done: Int, total: Int): String = "解析中... $done / $total 局面"
    const val ANALYZING_PREPARING = "解析準備中..."

    const val NOTIF_ANALYZING_TITLE = "棋譜解析中"
    fun notifProgress(done: Int, total: Int, progressPct: Int): String = "$done / $total 局面 ($progressPct%)"
    const val NOTIF_PREPARING = "準備中..."
    const val NOTIF_DONE_TITLE = "解析完了"
    const val NOTIF_DONE_TEXT = "棋譜の解析が完了しました。タップしてレポートを確認"
    const val NOTIF_ERROR_TITLE = "解析エラー"
    const val UNKNOWN_ERROR = "不明なエラー"

    // ═══ 6. レポート画面（棋譜ビューア・悪手カード）═══════════════════════════

    const val BACK = "戻る"
    const val TAB_MAINLINE = "本譜"
    /**
     * 「最善の変化」タブのラベル（固定文字列）。
     * 動的サフィックスは廃止——起点情報はカード選択状態と現在手ラベルで伝わる。
     */
    const val TAB_BEST_PV = "最善の変化"
    const val VIEWER_START_POSITION = "開始局面"
    fun viewerPlyLabel(ply: Int): String = "${ply}手目"
    const val NO_BLUNDERS_WIN = "悪手は見つかりませんでした。会心の一局です！"
    const val NO_BLUNDERS_UNKNOWN = "悪手は見つかりませんでした。"
    /** ユーザーが負けた場合の悪手ゼロメッセージ。endReasonLabel = "投了" / "切れ負け" 等。 */
    fun noBlundersLoss(endReasonLabel: String): String =
        "大きな悪手はありませんでした（結果: $endReasonLabel）。" +
        "内容は悪くない負けです——時間配分や小さな形勢の目減りが敗因かもしれません。"

    // 悪手カード
    fun blunderCardPly(ply: Long): String = "${ply}手目"
    const val BLUNDER_CARD_ACTUAL = "実戦"
    const val BLUNDER_CARD_BEST = "最善"

    // ═══ 7. ドリル画面 ════════════════════════════════════════════════════════

    const val DRILL_TITLE = "ドリル／次の一手"
    const val DRILL_EMPTY_TITLE = "ドリルの対象がありません"
    const val DRILL_EMPTY_BODY = "棋譜を解析すると悪手が出題対象になります。"
    const val DRILL_BACK_HOME = "ホームに戻る"
    const val DRILL_JUDGING = "判定中..."
    const val DRILL_PROMOTE_TITLE = "成りますか？"
    const val DRILL_PROMOTE_YES = "成る"
    const val DRILL_PROMOTE_NO = "成らない"
    fun drillAttemptCount(count: Int): String = "この問題の解答回数: ${count}回"
    fun drillTotalCount(count: Int): String = "全${count}問"
    const val DRILL_GIVE_UP = "答えを見る"
    const val DRILL_CORRECT = "正解。"
    const val DRILL_INCORRECT = "不正解"
    const val DRILL_YOUR_MOVE = "あなたの手"
    const val DRILL_BEST_MOVE = "最善手"
    fun drillLossPct(pct: String): String = "勝率損失 −${pct}%"
    fun drillActualMove(moveDisplay: String): String = "実戦での手: $moveDisplay"
    fun drillCategory(label: String): String = "分類: $label"
    fun drillNote(note: String): String = "根拠: $note"
    const val DRILL_GO_HOME = "ホームへ"
    const val DRILL_NEXT = "次の問題"

    // ═══ 19. 棋譜一覧・直近の解析 ════════════════════════════════════════════

    /** ホーム画面の「直近の解析」見出し。 */
    const val HOME_RECENT_ANALYSES = "直近の解析"
    /** ホーム画面の「すべて見る」リンク。 */
    const val HOME_VIEW_ALL = "すべて見る"
    /** 棋譜一覧画面のタイトル。 */
    const val GAME_LIST_TITLE = "棋譜一覧"
    /** ゲームカードの勝利バッジ。 */
    const val GAME_RESULT_WIN = "勝ち"
    /** ゲームカードの敗北バッジ。 */
    const val GAME_RESULT_LOSS = "負け"

    // ═══ 20. ドリル結果ビューア ══════════════════════════════════════════════

    /** ドリル結果 KifuLineViewer のタブ: ユーザーの手筋。 */
    const val DRILL_VIEWER_TAB_YOUR = "あなたの手"
    /** ドリル結果 KifuLineViewer のタブ: 最善手筋。 */
    const val DRILL_VIEWER_TAB_BEST = "最善手"

    // ═══ 21. 棋譜クリップボードコピー ════════════════════════════════════════

    /** レポート画面コピーアイコンの contentDescription。 */
    const val KIF_COPY_ICON_DESC = "棋譜をコピー"
    /** 棋譜コピー後のSnackbarメッセージ。 */
    const val KIF_COPIED_MESSAGE = "棋譜をコピーしました"

    // ═══ 25. 最善の変化タブの形勢表示 ════════════════════════════════════════
    // 最善の変化はエンジンPVのため線に沿って形勢はほぼ一定。分岐点の評価値
    // （blunder_report.cp_before）を全plyで常時表示する（手送りで値は変えない）。
    // ナビ行のラベルに evalSuffix() で直接連結する方式で表示する。

    /**
     * ドリル結果画面・レポート画面のナビ行ラベルに付ける形勢サフィックス（例:「（−350）」）。
     * No-jitter対応（DESIGN.md Layout節）: 別行のスロットではなく、既存のナビラベル
     * （「N手目 ▲notation」）に半角スペース＋括弧で連結して1行に収める。
     */
    fun evalSuffix(label: String): String = "（$label）"

    /**
     * 形勢サフィックスに使う「不明・失敗」プレースホルダー。
     * 読み筋延長エラー時・検討モードの評価失敗時に evalSuffix() 経由で
     * 「（—）」として表示する（詳細メッセージは表示しない）。
     */
    const val EVAL_UNAVAILABLE = "—"

    /**
     * 形勢サフィックスに使う「解析中」プレースホルダー。
     * 検討モードでエンジン評価が完了するまで evalSuffix() 経由で「（…）」を表示する。
     */
    const val EVAL_LOADING = "…"

    // ═══ 8. エラー ════════════════════════════════════════════════════════════

    fun errorMessage(message: String): String = "エラー: $message"
    fun gameNotFound(gameId: Long): String = "ゲームが見つかりません: $gameId"

    // ═══ 9. 対局者名（レポート） ═══════════════════════════════════════════════

    const val PLAYER_YOU = "（あなた）"
    const val PLAYER_UNKNOWN = "不明"

    // ═══ 10. 棋譜リスト ═══════════════════════════════════════════════════════

    const val MOVE_LIST_TITLE = "指し手一覧"

    // ═══ 23. この一局の指し手の強さ ═══════════════════════════════════════════

    /**
     * 悪手カードリスト先頭・caption行のプレフィックス（値は Mono で続く）。
     * 1局のみの推定は誤差が大きい＋上振れバイアスがあるため「参考値」と明示する。
     */
    const val GAME_STRENGTH_PREFIX = "この一局からの推定棋力（偏差値・参考値）: "

    // ═══ 11. 認証エラーメッセージ（匿名認証 v1）════════════════════════════════

    const val AUTH_ERROR_NETWORK = "ネットワークに接続できません。接続を確認してお試しください"
    const val AUTH_ERROR_ANON_SIGN_IN_GENERIC = "データ提供の開始に失敗しました。時間をおいてお試しください"
    const val AUTH_ERROR_DELETE_GENERIC = "データの削除に失敗しました。時間をおいてお試しください"

    // ═══ 14. アカウント（棋譜提供）画面 ═════════════════════════════════════

    const val ACCOUNT_SECTION_TITLE = "棋譜提供"
    const val ACCOUNT_NOT_PROVIDING_DESCRIPTION =
        "棋譜と解析結果を匿名でサーバーに提供できます。アプリの機能改善（棋力推定、悪手判定等アプリの機能に" +
        "関連する統計分析や機械学習モデルの作成・改善を含む）に利用されることがあります。"
    const val ACCOUNT_DEVICE_TRANSFER_NOTE =
        "機種変更時、提供済みデータの紐付けは引き継がれません"
    const val ACCOUNT_ENABLE_BUTTON = "データ提供を有効にする"
    const val ACCOUNT_PROVIDING_STATUS = "提供中"
    fun accountUploadedCount(count: Int): String = "アップロード済み ${count}局"
    const val ACCOUNT_DISABLE_BUTTON = "提供をやめてデータを削除"
    const val ACCOUNT_DELETE_DIALOG_TITLE = "提供をやめますか？"
    const val ACCOUNT_DELETE_DIALOG_TEXT =
        "サーバー上の棋譜・解析結果がすべて削除されます。この操作は取り消せません"
    const val ACCOUNT_DELETE_CONFIRM = "削除する"
    const val ACCOUNT_AUTO_UPLOAD_LABEL = "解析後に自動アップロード"
    const val ACCOUNT_AUTO_UPLOAD_DESC = "解析完了後に棋譜を自動で送信します"

    /** 手動アップロードボタン（提供中画面）。count = 未アップロード件数。 */
    fun accountManualUploadButton(count: Int): String = "未アップロードの棋譜 ${count}局をアップロード"
    /** アップロード結果メッセージ。success = 成功件数、failed = 失敗件数。 */
    fun accountUploadResult(success: Int, failed: Int): String =
        "アップロード完了: 成功${success}局${if (failed > 0) " / 失敗${failed}局" else ""}"

    // ═══ 12. 申告棋力表示
    /** StrengthCard の申告棋力行プレフィックス。 */
    const val STRENGTH_DECLARED_PREFIX = "申告: "

    /** サービスIDから短縮表示名。申告棋力行に使用。 */
    fun serviceShortName(serviceId: String): String = when (serviceId) {
        "shogi_wars" -> "ウォーズ"
        "kiou" -> "棋桜"
        else -> serviceId
    }

    /** ルールIDから表示名（warsRules / kiouRules から検索）。 */
    fun ruleLabel(serviceId: String, ruleId: String): String {
        val list = when (serviceId) {
            "shogi_wars" -> warsRules
            "kiou" -> kiouRules
            else -> emptyList()
        }
        return list.firstOrNull { it.first == ruleId }?.second ?: ruleId
    }

    /** 申告棋力行のフォーマット関数。 */
    fun strengthDeclaredLine(entries: String): String = "$STRENGTH_DECLARED_PREFIX$entries"

    // ═══ 13. 設定画面
    const val SETTINGS_TITLE = "設定"
    const val SETTINGS_SECTION_PROFILE = "プロフィール"
    const val SETTINGS_ROW_RATING = "棋力・アカウント名"
    const val SETTINGS_ROW_RATING_SUB = "サービス・ルール別の段級位（任意）"
    const val SETTINGS_SECTION_DATA = "データ"
    const val SETTINGS_ROW_ACCOUNT = "アカウント"
    const val SETTINGS_ROW_ACCOUNT_SUB = "ログイン・棋譜のアップロード・削除"
    const val SETTINGS_SECTION_DISPLAY = "表示"
    const val SETTINGS_ROW_THEME = "テーマ"
    const val THEME_DIALOG_TITLE = "テーマ"
    const val THEME_SYSTEM = "システムに従う"
    const val THEME_LIGHT = "ライト"
    const val THEME_DARK = "ダーク"
    /** theme_mode 値（'system'/'light'/'dark'）を表示名に変換する。 */
    fun themeLabel(themeMode: String): String = when (themeMode) {
        "light" -> THEME_LIGHT
        "dark" -> THEME_DARK
        else -> THEME_SYSTEM
    }
    const val SETTINGS_SECTION_ABOUT = "このアプリについて"
    const val SETTINGS_ROW_HELP = "ヘルプ"
    const val SETTINGS_ROW_FEEDBACK = "フィードバック（X）"
    const val SETTINGS_ROW_TERMS = "利用規約・プライバシーポリシー"
    const val SETTINGS_ROW_LICENSES = "オープンソースライセンス"
    const val SETTINGS_ROW_VERSION = "バージョン"

    // ═══ 15. クリップボード棋譜登録 ═══════════════════════════════════════════

    const val KIF_SOURCE_TITLE = "棋譜の追加"
    const val KIF_SOURCE_FILE = "ファイルから選ぶ"
    const val KIF_SOURCE_CLIPBOARD = "クリップボードから貼り付け"
    const val KIF_CLIPBOARD_EMPTY = "クリップボードにテキストがありません"
    const val KIF_CLIPBOARD_INVALID = "クリップボードに棋譜（KIF）が見つかりませんでした"
    /** クリップボード棋譜の表示用ファイル名。dateStr = "2026-07-14 09:30" 形式。 */
    fun clipboardFileName(dateStr: String): String = "クリップボード $dateStr"

    /** ファイルからの取込（iOS DocumentPicker）のエラーメッセージ。 */
    const val KIF_FILE_EMPTY = "選択したファイルにテキストがありません"
    const val KIF_FILE_INVALID = "選択したファイルに棋譜（KIF）が見つかりませんでした"

    // ─── ヘルプ画面（Settings → ヘルプ / 推定棋力カードの「?」）───────────────
    // 推定棋力カードの「?」は説明ダイアログではなくヘルプ画面（推定棋力の節）へ
    // 直接遷移する。

    const val HELP_SCREEN_TITLE = "ヘルプ"

    const val HELP_SECTION_HOW = "このアプリの仕組み"
    const val HELP_HOW_BODY =
        "棋譜を読み込むと、端末内の将棋エンジン（やねうら王＋Háo評価関数）が全局面を解析します。" +
        "解析はすべて端末内で完結し、棋譜が外部に送られることはありません（データ提供を有効にした場合を除く）。\n\n" +
        "解析結果からあなたの悪手を抽出し、性質で6種類に分類します（詰み逃し・頓死・タダ取られ・" +
        "手筋による駒損・玉の危険・形勢のミス）。\n\n" +
        "公開対局 約1.1万局・107万局面の研究データと照合し、「あなたの棋力帯で標準的に起きるミスか」" +
        "を判定します。棋力帯相応のミスだけがドリルになります。"

    const val HELP_SECTION_STRENGTH = "推定棋力（偏差値）とは"
    const val HELP_STRENGTH_BODY =
        "将棋サプリ独自の指標です。あなたの実測悪手率から強さを推定し、研究データの対局者集団" +
        "（オンライン将棋のレート対局者 約1,900人）の中での相対位置を偏差値で表します。" +
        "模試の偏差値と同じで、50がこの集団の平均、60で上位16%、40で下位16%が目安です。\n\n" +
        "「±」は誤差幅（90%信頼幅の目安）です。手数を積むと縮みますが、個人差の系統誤差が" +
        "残るため±22程度で頭打ちになります。各サービスの公式レートや申告棋力とは独立した値です。"

    const val HELP_SECTION_VERDICTS = "◎○△の意味"
    const val HELP_VERDICTS_BODY =
        "◎ 優先出題: 上位の棋力帯ではほぼ起きないミスです。あなたの棋力帯では頻繁に起きており、訓練で消せる可能性が高い。\n\n" +
        "○ 出題対象: あなたの棋力帯で標準的に起きるミスです。この棋力帯を抜けるために取り組むべき課題。\n\n" +
        "△ 見送り: どの棋力帯でも稀なうっかりです。訓練効率が低いため出題対象にしません。"

    const val HELP_SECTION_DATA = "データ提供とは"
    const val HELP_DATA_BODY =
        "任意・匿名での提供です。設定→アカウントから有効にした場合のみ、棋譜と解析結果が" +
        "研究目的（解析品質の向上）に利用されます。端末を変えた場合、" +
        "提供済みデータの紐付けは引き継がれません。"

    /** Webヘルプ（詳しい仕組み・研究データの出典）への導線。 */
    const val HELP_OPEN_WEB = "詳しい仕組み・データの出典について（Web）"

    // ═══ 18. 形勢の表示単位（eval_display）═══════════════════════════════════════

    /** 設定「表示」セクションの「形勢の表示」行ラベル。 */
    const val SETTINGS_ROW_EVAL_DISPLAY = "形勢の表示"
    /** 形勢の表示選択ダイアログのタイトル。 */
    const val EVAL_DISPLAY_DIALOG_TITLE = "形勢の表示"
    /** 選択肢: 評価値モード（cp）。 */
    const val EVAL_DISPLAY_CP = "評価値"
    /** 選択肢: 勝率モード（wp）。 */
    const val EVAL_DISPLAY_WP = "勝率"
    /** eval_display 値（'cp'/'wp'）を表示名に変換する。 */
    fun evalDisplayLabel(mode: String): String = if (mode == "cp") EVAL_DISPLAY_CP else EVAL_DISPLAY_WP

    /**
     * 評価値モードの損失表示。
     * cpLoss = cp_before + cp_after（手番側の cp 損失量）。
     * 符号は DESIGN.md の規約に従い「−」必須。
     */
    fun blunderLossCp(cpLoss: Int): String = "−$cpLoss"

    /**
     * 詰み絡みの損失表示（|cp| >= 29000 相当）。
     * 詰み確定局面（詰みまで1手など）は数値ではなく「詰み」で表す。
     */
    const val BLUNDER_LOSS_MATE = "詰み"

    /**
     * 悪手後に相手から詰まされる状態（変化後の表示ラベル）。
     * cpAfter >= 29000（相手視点の評価値が詰み相当）のとき使用する。
     * 定義: userAfterCp = -cpAfter <= -29000 → 相手玉に詰みが生じている。
     */
    const val BLUNDER_AFTER_MATED = "詰まされ"

    /**
     * cp を符号付き文字列に変換する（悪手カードの変化前後表示用）。
     * 例: 120 → "+120"、-450 → "−450"、0 → "±0"
     * 符号規約: 正=「+」、負=「−」（DESIGN.md: 全角マイナス）。
     */
    fun cpSignedLabel(cp: Int): String = when {
        cp > 0 -> "+$cp"
        cp < 0 -> "−${-cp}"
        else -> "±0"
    }

    /**
     * レポート手送り時の局面評価値: 勝率モード表示。
     * ユーザー視点の勝率（%）。例: 62 → "勝率62%"
     */
    fun positionEvalWp(pct: Int): String = "勝率$pct%"

    /**
     * レポート手送り時の局面評価値: 詰み表示。
     * userMate = ユーザー視点の詰み手数（正 = 自分が詰ます、負 = 詰まされる）。
     * 符号は cpSignedLabel と同じ規約（+は半角、−は全角マイナス U+2212）。
     * 色分けは表示側（EvalLabel.sign）が担うため、ここでは「勝ち／負け」の文言を持たない。
     */
    fun positionEvalMate(userMate: Int): String =
        if (userMate > 0) "+${userMate}手詰" else "−${-userMate}手詰"

    /** mate_in=0 局面の詰み表示（ユーザーが詰ました側）。符号規約は positionEvalMate と同じ。 */
    const val POSITION_EVAL_MATE_ZERO_WIN = "+詰み"
    /** mate_in=0 局面の詰み表示（ユーザーが詰まされた側）。符号規約は positionEvalMate と同じ。 */
    const val POSITION_EVAL_MATE_ZERO_LOSS = "−詰み"

    // ═══ 22. 先後確認の省略 ══════════════════════════════════════════════════

    /** 側選択ダイアログのチェックボックス（アカウント名一致時のみ表示）。 */
    const val SKIP_SIDE_CONFIRM_CHECKBOX = "次回からこの確認を省略"
    /** 設定画面の行ラベル。 */
    const val SETTINGS_ROW_SKIP_SIDE_CONFIRM = "先後確認の省略"
    /** 設定画面の行サブテキスト。 */
    const val SETTINGS_ROW_SKIP_SIDE_CONFIRM_SUB = "アカウント名が一致したら確認せず解析を開始"

    // ═══ 17. デバッグ（DEBUG ビルドのみ設定画面に表示）═══════════════════════════

    /** 設定画面のデバッグセクション見出し。 */
    const val SETTINGS_DEBUG_SECTION = "デバッグ"

    /** 設定画面のデバッグ行ラベル（DebugScreen への導線）。 */
    const val SETTINGS_DEBUG_ROW = "デバッグ画面"

    /** レポート画面の駒台配置を左右にする実機評価用トグル。 */

    /** DebugScreen の「完了通知を送付」ボタン。 */
    const val DEBUG_SEND_NOTIFICATION = "完了通知を送付"

    // ═══ 26. レポート画面の検討モード ════════════════════════════════════════

    /**
     * 検討中バナー（タブ行の代わり）。originAbsolutePly = 検討開始局面の本譜上の絶対手数（0 = 開始局面）。
     * 手番を含める（着手ごとに更新）。senteToMove = 現在の検討局面が先手番なら true。
     */
    fun studyBanner(originAbsolutePly: Int, senteToMove: Boolean): String {
        val turn = if (senteToMove) "▲番" else "△番"
        return if (originAbsolutePly <= 0) "検討中 — 開始局面から（$turn）"
               else "検討中 — ${originAbsolutePly}手目から（$turn）"
    }

    /**
     * 手番でない側の駒をタップしたときのヒント。evalSuffix() 経由で検討ナビ行の
     * ラベル末尾に表示する。エラーではないので朱は使わず ink2 で表示する。次の正常タップで消える。
     */
    fun studyTurnHint(senteToMove: Boolean): String =
        if (senteToMove) "▲番です" else "△番です"

    /** 検討中バナーの「終了」ボタン（ghost）。 */
    const val STUDY_END = "終了"

    /** 検討ナビ行のラベル: 検討開始局面（moves が空）。 */
    const val STUDY_START_POSITION = "検討開始局面"

    /** 検討ナビ行のラベル: N手目（検討開始局面からの手数）。 */
    fun studyPlyLabel(ply: Int): String = "検討${ply}手目"

    // 検討モードの評価エラーはナビ行に evalSuffix(EVAL_UNAVAILABLE) で表示する。

    // ═══ 27. レポート画面トップバー ══════════════════════════════════════════
    // トップバーは32dpの1行インライン行（MainActivity.kt ReportScreen）。
    // 棋戦名使用時のファイル名は3行目として表示せず、Info アイコンの
    // 対局情報ダイアログに集約する。

    /** トップバー: 対局情報アイコンの contentDescription。 */
    const val GAME_INFO_ICON_DESC = "対局情報"
    /** 対局情報ダイアログのタイトル。 */
    const val GAME_INFO_DIALOG_TITLE = "この棋譜について"
    /** 対局情報ダイアログの閉じるボタン。 */
    const val GAME_INFO_CLOSE = "閉じる"

    // ═══ 28. iOS CMPデモ（:ui MainViewController の画面切り替え）══════════════
    // デモは HomeScreen・ReportScreen・DrillScreen 側の既存 AppStrings をそのまま使うため、
    // デモ専用の文字列はここにはない。

    // ═══ 29. OSSライセンス画面（iOS/Android共通）══════════════════════════════
    // :ui commonMain の LicenseInfoScreen が唯一の実装（Android/iOSとも同じ画面を使う）。
    // 依存OSSの完全な一覧は AboutLibraries（Libs）を LibrariesContainer に渡して描画するため、
    // 手動要約の文字列は持たない（旧 LICENSE_OSS_HEADER/BODY は撤去）。
    // LICENSE_OSS_LIST_HEADER より後ろの一覧本体は LicenseInfoScreen が Libs から描画する。

    const val LICENSE_SCREEN_TITLE = "ライセンス"
    const val LICENSE_APP_HEADER = "本アプリのライセンス"
    const val LICENSE_APP_BODY =
        "将棋サプリは GNU General Public License v3.0（GPLv3）のもとで" +
        "公開されるオープンソースソフトウェアです。"

    const val LICENSE_ENGINE_HEADER = "同梱している将棋エンジン・評価関数"
    const val LICENSE_ENGINE_BODY =
        "・やねうら王（YaneuraOu） — GPLv3\n" +
        "・Háo 評価関数 — GPLv3"

    const val LICENSE_FONT_HEADER = "フォントライセンス"
    const val LICENSE_FONT_INTRO =
        "本アプリは以下のフォントを SIL Open Font License 1.1（OFL-1.1）のもとで使用しています。" +
        "OFL の全文はソースリポジトリに同梱しています。"
    const val LICENSE_FONT_BODY =
        "・Shippori Mincho — SIL Open Font License 1.1\n" +
        "・IBM Plex Sans JP — SIL Open Font License 1.1\n" +
        "・IBM Plex Mono — SIL Open Font License 1.1"

    const val LICENSE_OSS_LIST_HEADER = "使用しているOSSライブラリ"

    const val LICENSE_SOURCE_HEADER = "ソースリポジトリ"
    /** タップでリポジトリURLを開くリンクの表示文言。実URL値はプラットフォーム側の定数を使う。 */
    const val LICENSE_SOURCE_URL = "https://github.com/hmiyado/shogi-supplement"
}
