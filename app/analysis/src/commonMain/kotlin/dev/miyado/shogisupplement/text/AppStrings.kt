package dev.miyado.shogisupplement.text

/**
 * 動的な値はテンプレート関数の引数で受け取る。
 */
object AppStrings {

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
     * @param n 実際の見逃し手数。係数表の手数バケットではない。
     * 「詰将棋」にしない理由: エンジンのmateは連続王手の詰み筋とは限らない。
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
     * 境界は帯端レート（1300/1600/1900/2200）を [StrengthNorm] v1 で換算した値。
     * 帯名を変えない理由: 係数表・DB保存noteの正規化キーとして流通している。
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


    const val APP_TITLE = "将棋サプリ"
    const val HOME_OPEN_KIF = "棋譜を追加する"
    const val HOME_TODAYS_DRILL_TITLE = "今日の1問"
    fun homeTodaysDrillPly(ply: Long): String = "${ply}手目の局面"
    const val HOME_TODAYS_DRILL_TAP = "タップして次の一手問題を解く"
    const val HOME_PAST_ANALYSES = "過去の解析"
    const val HOME_NO_GAMES = "まだ解析した棋譜がありません。\n「棋譜を追加する」から.kifファイルを選ぶか、コピーした棋譜を貼り付けてください。"

    const val STRENGTH_CARD_TITLE = "推定棋力（偏差値）"
    fun strengthDetail(gameCount: Int): String = "直近${gameCount}局から算出"

    fun gameMoveCount(count: Long): String = "${count}手"
    fun playersLine(senteName: String?, goteName: String?): String =
        "先手: ${senteName ?: "不明"}  後手: ${goteName ?: "不明"}"


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

    // 匿名アカウント作成の事前確認。アカウント削除後など未ログインで棋譜を追加すると、
    // 解析時に匿名アカウントが新規作成されるため、追加の入口で伝えて確認を取る
    const val IMPORT_ACCOUNT_NOTICE_TITLE = "アカウントの作成"
    const val IMPORT_ACCOUNT_NOTICE_BODY =
        "棋譜を解析すると匿名アカウントを作成します。作らなくても、端末内での解析は使えます。"
    const val IMPORT_ACCOUNT_NOTICE_CONTINUE = "続ける"
    const val IMPORT_ACCOUNT_NOTICE_DECLINE = "作らずに解析"

    /** アカウントを作らずに使っている端末にだけ出す、あとから作るための導線。 */
    const val SETTINGS_CREATE_ACCOUNT = "アカウントを作成する"
    const val SETTINGS_CREATE_ACCOUNT_SUB = "サーバーでの解析・引き継ぎが使えるようになります"

    const val SIDE_DIALOG_TITLE = "自分の側"
    fun sideSente(senteName: String?): String = if (senteName != null) "先手（$senteName）" else "先手"
    fun sideGote(goteName: String?): String = if (goteName != null) "後手（$goteName）" else "後手"
    const val START_ANALYSIS = "解析開始"

    const val PENDING_ANALYSIS_BADGE = "未解析"
    const val PENDING_ANALYSIS_TITLE = "解析していない棋譜です"
    const val PENDING_ANALYSIS_BODY = "解析すると、悪手と推定棋力を確認できます。"
    const val ANALYZE_GAME = "解析する"

    /** ホーム一覧の解析中カードに出すバッジ文言。 */
    const val ANALYZING_BADGE = "解析中"

    /**
     * Why not 局面数（0手目の初期局面込み）表示: 解析対象は0..moves.sizeの局面数だが、
     * ユーザーはNを手数として読むため、局面数のまま出すと盤・グラフより1手遅れて見える。
     * @param currentMove 盤・グラフ先端と同じ、0手目を除いた手数。
     */
    fun analyzingProgress(currentMove: Int, totalMoves: Int): String = "解析中... $currentMove / $totalMoves 手"

    /**
     * レポート画面の進捗バナーと同じスロットに完了直後だけ表示する一時メッセージ。
     * 通知タイトル（[NOTIF_DONE_TITLE]="解析完了"）とは別文言にする
     * （画面内の一時表示と通知を語感で混同させないため）。
     */
    const val ANALYSIS_COMPLETED_BANNER = "解析が完了しました"

    const val NOTIF_ANALYZING_TITLE = "棋譜解析中"
    fun notifProgress(done: Int, total: Int, progressPct: Int): String = "$done / $total 局面 ($progressPct%)"
    const val NOTIF_PREPARING = "準備中..."
    const val NOTIF_DONE_TITLE = "解析完了"
    const val NOTIF_DONE_TEXT = "棋譜の解析が完了しました。タップしてレポートを確認"
    const val NOTIF_ERROR_TITLE = "解析エラー"
    const val UNKNOWN_ERROR = "不明なエラー"


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

    fun blunderCardPly(ply: Long): String = "${ply}手目"
    const val BLUNDER_CARD_ACTUAL = "実戦"
    const val BLUNDER_CARD_BEST = "最善"


    const val DRILL_TITLE = "次の一手"
    const val DRILL_EMPTY_TITLE = "次の一手問題がありません"
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


    const val HOME_RECENT_ANALYSES = "直近の解析"
    const val HOME_VIEW_ALL = "すべて見る"
    const val GAME_LIST_TITLE = "棋譜一覧"
    const val GAME_RESULT_WIN = "勝ち"
    const val GAME_RESULT_LOSS = "負け"


    const val GAME_LIST_FILTER_SOURCE = "出典"
    const val GAME_LIST_FILTER_SIDE = "先後"
    const val PLAYER_SIDE_SENTE = "先手"
    const val PLAYER_SIDE_GOTE = "後手"
    const val GAME_LIST_FILTER_RESULT = "勝敗"
    const val GAME_LIST_FILTER_PERIOD = "期間"
    const val GAME_LIST_FILTER_SOURCE_OTHER = "その他"
    const val GAME_LIST_FILTER_PERIOD_7D = "直近7日"
    const val GAME_LIST_FILTER_PERIOD_30D = "直近30日"
    const val GAME_LIST_FILTER_CLEAR = "絞り込みを解除"
    fun gameListFilteredCount(shown: Int, total: Int): String = "${shown} / ${total}件"
    fun gameListTotalCount(total: Int): String = "${total}件"
    /** 一覧上部の絞り込みボタンのラベル・アイコンのcontentDescription。 */
    const val GAME_LIST_FILTER_BUTTON = "絞り込み"
    const val GAME_LIST_FILTER_SHEET_TITLE = "絞り込み条件"
    /** 絞り込み条件ボトムシートの適用ボタン（条件を確定して一覧に反映する）。 */
    const val GAME_LIST_FILTER_APPLY = "検索"


    /** ドリル結果 KifuLineViewer のタブ: ユーザーの手筋。 */
    const val DRILL_VIEWER_TAB_YOUR = "あなたの手"
    /** ドリル結果 KifuLineViewer のタブ: 最善手筋。 */
    const val DRILL_VIEWER_TAB_BEST = "最善手"


    /** レポート画面コピーアイコンの contentDescription。 */
    const val KIF_COPY_ICON_DESC = "棋譜をコピー"
    /** 棋譜コピー後のSnackbarメッセージ。 */
    const val KIF_COPIED_MESSAGE = "棋譜をコピーしました"

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
     * 評価失敗時は詳細を出さず「（—）」と表示する。
     */
    const val EVAL_UNAVAILABLE = "—"


    fun errorMessage(message: String): String = "エラー: $message"
    fun gameNotFound(gameId: Long): String = "ゲームが見つかりません: $gameId"


    const val PLAYER_YOU = "（あなた）"
    const val PLAYER_UNKNOWN = "不明"


    const val MOVE_LIST_TITLE = "指し手一覧"


    /**
     * 悪手カードリスト先頭・caption行のプレフィックス（値は Mono で続く）。
     * 1局のみの推定は誤差が大きい＋上振れバイアスがあるため「参考値」と明示する。
     */
    const val GAME_STRENGTH_PREFIX = "この一局からの推定棋力（偏差値・参考値）: "


    const val AUTH_ERROR_NETWORK = "ネットワークに接続できません。接続を確認してお試しください"
    const val AUTH_ERROR_ANON_SIGN_IN_GENERIC = "データ提供の開始に失敗しました。時間をおいてお試しください"
    const val AUTH_ERROR_DELETE_GENERIC = "データの削除に失敗しました。時間をおいてお試しください"


    const val ACCOUNT_SECTION_TITLE = "アカウント"
    const val ACCOUNT_NOT_PROVIDING_DESCRIPTION =
        "匿名のアカウントを作成すると、サーバーでの解析が使えます。名前・メールアドレスの入力は必要ありません。"
    const val ACCOUNT_DEVICE_TRANSFER_NOTE =
        "サーバーに保存した棋譜は、引き継ぎコードで別の端末へ移せます"
    const val ACCOUNT_ENABLE_BUTTON = "アカウントを作成"
    const val ACCOUNT_PROVIDING_STATUS = "作成済み"
    fun accountUploadedCount(count: Int): String = "サーバーに保存した棋譜 ${count}局"
    const val ACCOUNT_DISABLE_BUTTON = "アカウントを削除"
    const val ACCOUNT_DELETE_DIALOG_TITLE = "アカウントを削除しますか？"
    const val ACCOUNT_DELETE_DIALOG_TEXT =
        "サーバー上の棋譜・解析結果がすべて削除されます。この操作は取り消せません"
    const val ACCOUNT_UPLOAD_SECTION = "棋譜の保存"
    const val ACCOUNT_DELETE_CONFIRM = "削除する"
    const val GAME_DELETE_ICON_DESC = "棋譜を削除"
    const val GAME_DELETE_DIALOG_TITLE = "この棋譜を削除しますか？"
    const val GAME_DELETE_DIALOG_TEXT_DEVICE_ONLY =
        "解析結果も含め、端末から削除されます。この操作は取り消せません。"
    const val GAME_DELETE_DIALOG_TEXT_WITH_SERVER =
        "解析結果も含め、端末・サーバーから削除されます。この操作は取り消せません。"
    const val GAME_DELETE_SERVER_CHECKBOX_LABEL = "サーバーに保存した棋譜も削除する"
    const val GAME_DELETE_SERVER_ERROR =
        "サーバーからの削除に失敗しました。時間をおいてお試しください"
    const val GAME_DELETE_CONFIRM = "削除する"
    const val ACCOUNT_AUTO_UPLOAD_LABEL = "棋譜をサーバーに保存する"
    const val ACCOUNT_AUTO_UPLOAD_DESC =
        "解析した棋譜を保存します。研究・改善に匿名で利用され、引き継ぎのもとになります"

    /** 手動アップロードボタン（提供中画面）。count = 未アップロード件数。 */
    fun accountManualUploadButton(count: Int): String = "未アップロードの棋譜 ${count}局をアップロード"
    /** アップロード結果メッセージ。棋譜は局、次の一手の成績は件で数える。 */
    fun accountUploadResult(success: Int, failed: Int): String =
        accountUploadResult(success, failed, drillPendingRemaining = 0)

    /** 手動アップロード完了メッセージ。次の一手の成績未送信数が残る場合だけ付記する。 */
    fun accountUploadResult(
        gameSuccess: Int,
        gameFailed: Int,
        drillPendingRemaining: Int,
    ): String {
        val base = "アップロード完了: 成功${gameSuccess}局${if (gameFailed > 0) " / 失敗${gameFailed}局" else ""}"
        return if (drillPendingRemaining > 0) {
            "$base／次の一手の成績は${drillPendingRemaining}件送信できませんでした"
        } else {
            base
        }
    }

    /** StrengthCard の申告棋力行プレフィックス。 */
    const val STRENGTH_DECLARED_PREFIX = "申告: "

    /** サービスIDから短縮表示名。申告棋力行に使用。 */
    fun serviceShortName(serviceId: String): String = when (serviceId) {
        "shogi_wars" -> "ウォーズ"
        "kiou" -> "棋桜"
        else -> serviceId
    }

    /**
     * `source_place`の表示ラベル。"other"・null・未知値はnull。
     */
    fun sourcePlaceLabel(sourcePlace: String?): String? = when (sourcePlace) {
        "wars" -> "将棋ウォーズ"
        "lishogi" -> "lishogi"
        "kiou" -> "棋桜"
        else -> null
    }

    /**
     * 出典フィルタチップ用のラベル。他の出典表示と異なり "other" にも専用のラベルを
     * 割り当てる（フィルタチップは選択肢として常にラベルが要るため、
     * フォールバック用途の表示関数とは別にする）。
     */
    fun sourceFilterLabel(sourcePlace: String): String =
        sourcePlaceLabel(sourcePlace) ?: GAME_LIST_FILTER_SOURCE_OTHER

    /** ルールIDから表示名（warsRules / kiouRules から検索）。 */
    fun ruleLabel(serviceId: String, ruleId: String): String {
        val list = when (serviceId) {
            "shogi_wars" -> warsRules
            "kiou" -> kiouRules
            else -> emptyList()
        }
        return list.firstOrNull { it.first == ruleId }?.second ?: ruleId
    }

    /** サービスIDから表示名（serviceOptions から検索）。省スペースな [serviceShortName] と違い、正式名を返す。 */
    fun serviceLabel(serviceId: String): String =
        serviceOptions.firstOrNull { it.first == serviceId }?.second ?: serviceId

    /** 申告棋力行のフォーマット関数。 */
    fun strengthDeclaredLine(entries: String): String = "$STRENGTH_DECLARED_PREFIX$entries"

    // ═══ 13. 設定画面
    const val SETTINGS_TITLE = "設定"
    const val SETTINGS_SECTION_PROFILE = "プロフィール"
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
    const val SETTINGS_ROW_RELEASE_NOTES = "リリースノート"
    const val SETTINGS_ROW_LICENSES = "オープンソースライセンス"
    const val SETTINGS_ROW_VERSION = "バージョン"

    // ═══ 15. クリップボード棋譜登録 ═══════════════════════════════════════════

    const val KIF_SOURCE_TITLE = "棋譜の追加"
    const val KIF_SOURCE_FILE = "ファイルから選ぶ"
    const val KIF_SOURCE_CLIPBOARD = "クリップボードから貼り付け"
    const val KIF_SOURCE_MANUAL = "盤で入力する"
    const val KIF_CLIPBOARD_EMPTY = "クリップボードにテキストがありません"
    const val KIF_CLIPBOARD_INVALID = "クリップボードに棋譜（KIF）が見つかりませんでした"
    /** クリップボード棋譜の表示用ファイル名。dateStr = "2026-07-14 09:30" 形式。 */
    fun clipboardFileName(dateStr: String): String = "クリップボード $dateStr"

    /** ファイルからの取込（iOS DocumentPicker）のエラーメッセージ。 */
    const val KIF_FILE_EMPTY = "選択したファイルにテキストがありません"
    const val KIF_FILE_INVALID = "選択したファイルに棋譜（KIF）が見つかりませんでした"

    // ═══ 16. 手動棋譜入力 ════════════════════════════════════════════════════
    const val MANUAL_KIFU_TITLE = "盤で棋譜を入力"
    const val MANUAL_KIFU_DETAILS = "詳細"
    const val MANUAL_KIFU_INFO_TITLE = "対局の詳細"
    const val MANUAL_KIFU_INFO_DESCRIPTION = "棋譜と一緒に保存する情報を入力できます。必要な項目だけ入力してください。"
    const val MANUAL_KIFU_SENTE = "先手（任意）"
    const val MANUAL_KIFU_GOTE = "後手（任意）"
    const val MANUAL_KIFU_STARTED_AT = "対局日時"
    const val MANUAL_KIFU_PLACE = "対局場所（任意）"
    const val MANUAL_KIFU_FLIP = "盤を反転"
    const val MANUAL_KIFU_RESIGN = "投了"
    const val MANUAL_KIFU_PROMOTION_TITLE = "成り"
    const val MANUAL_KIFU_PROMOTION_BODY = "この手は成りますか？"
    const val MANUAL_KIFU_PROMOTE = "成る"
    const val MANUAL_KIFU_NOT_PROMOTE = "不成"
    const val MANUAL_KIFU_DISCARD_TITLE = "入力を破棄しますか？"
    const val MANUAL_KIFU_DISCARD_BODY = "入力した棋譜は保存されません。"
    const val MANUAL_KIFU_DISCARD = "破棄する"
    const val DONE = "完了"
    const val CLOSE = "閉じる"

    /** 端末エンジンを持たないビルドでサーバー解析が未設定のときのエラー。 */
    const val ANALYSIS_SERVER_NOT_CONFIGURED = "サーバー解析の設定が読み込めませんでした。時間をおいて再度お試しください"

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
     * @param userMate ユーザー視点の詰み手数。正は自分が詰ます、負は詰まされる。
     * 符号は半角+と全角マイナスU+2212を使う。
     */
    fun positionEvalMate(userMate: Int): String =
        if (userMate > 0) "+${userMate}手詰" else "−${-userMate}手詰"

    /** mate_in=0 局面の詰み表示（ユーザーが詰ました側）。符号規約は positionEvalMate と同じ。 */
    const val POSITION_EVAL_MATE_ZERO_WIN = "+詰み"
    /** mate_in=0 局面の詰み表示（ユーザーが詰まされた側）。符号規約は positionEvalMate と同じ。 */
    const val POSITION_EVAL_MATE_ZERO_LOSS = "−詰み"


    /** 側選択ダイアログのチェックボックス（アカウント名一致時のみ表示）。 */
    const val SKIP_SIDE_CONFIRM_CHECKBOX = "次回からこの確認を省略"
    /** 設定画面の行ラベル。 */
    const val SETTINGS_ROW_SKIP_SIDE_CONFIRM = "先後確認の省略"
    /** 設定画面の行サブテキスト。 */
    const val SETTINGS_ROW_SKIP_SIDE_CONFIRM_SUB = "アカウント名が一致したら確認せず解析を開始"


    /** 設定画面のデバッグセクション見出し。 */
    const val SETTINGS_DEBUG_SECTION = "デバッグ"

    /** 設定画面のデバッグ行ラベル（DebugScreen への導線）。 */
    const val SETTINGS_DEBUG_ROW = "デバッグ画面"

    /** レポート画面の駒台配置を左右にする実機評価用トグル。 */

    /** DebugScreen の「完了通知を送付」ボタン。 */
    const val DEBUG_SEND_NOTIFICATION = "完了通知を送付"

    /** iOS専用デバッグ画面（WASMバイナリの配信元URL切替）のタイトル。 */
    const val DEBUG_SCREEN_TITLE = "デバッグ: 配信元URL"

    const val DEBUG_WIPE_LOCAL_DATA = "端末内データをすべて削除"
    const val DEBUG_WIPE_DIALOG_TITLE = "端末内データをすべて削除しますか？"
    const val DEBUG_WIPE_DIALOG_TEXT =
        "棋譜・解析結果・設定・引き継ぎコードを消し、初回起動と同じ状態にします。" +
        "サーバー上のデータとダウンロード済みの解析エンジンは消えません。"
    const val DEBUG_WIPE_DONE = "削除しました。アプリを再起動してください"

    /** 配信元URL入力欄のラベル。 */
    const val DEBUG_WASM_SITE_FIELD_LABEL = "配信元URL"

    /** 配信元URL入力欄のプレースホルダ（ローカル配信の例）。 */
    const val DEBUG_WASM_SITE_PLACEHOLDER = "http://127.0.0.1:8925/"

    /** 現在有効な配信元URLの表示。[url]＝実際に使われるURL、[source]＝その由来ラベル。 */
    fun debugWasmSiteEffective(url: String, source: String): String = "現在有効: $url（$source）"

    /** 配信元の由来ラベル: 環境変数（KENTO_SITE_BASE_URL_OVERRIDE）が優先された。 */
    const val DEBUG_WASM_SITE_SOURCE_ENV = "環境変数"

    /** 配信元の由来ラベル: この画面での保存値が使われている。 */
    const val DEBUG_WASM_SITE_SOURCE_SAVED = "保存値"

    /** 配信元の由来ラベル: 環境変数・保存値のいずれも無く本番Pagesを使っている。 */
    const val DEBUG_WASM_SITE_SOURCE_PRODUCTION = "本番"

    /** 保存ボタン。 */
    const val DEBUG_WASM_SITE_SAVE = "保存"

    /** 保存値をクリアして本番配信へ戻すボタン。 */
    const val DEBUG_WASM_SITE_CLEAR = "本番に戻す"

    /** 保存失敗時（スキーム無し等の不正なURL）のエラー表示。 */
    const val DEBUG_WASM_SITE_INVALID = "URLの形式が正しくありません（例: http://127.0.0.1:8925/）"

    /** 保存成功時の一時表示。次回起動から反映される旨を明示する。 */
    const val DEBUG_WASM_SITE_SAVED = "保存しました。次回起動から有効になります。"


    /**
     * 手番でない側の駒をタップしたときのヒント。検討中はナビ行中央のラベルを
     * 一時的にこの文言へ置き換える（次の正常タップで通常のラベルに戻る）。
     */
    fun studyTurnHint(senteToMove: Boolean): String =
        if (senteToMove) "▲番です" else "△番です"

    const val STUDY_END = "終了"

    /** 検討ナビ行のラベル: 検討開始局面（moves が空）。 */
    const val STUDY_START_POSITION = "検討開始局面"

    /** 検討ナビ行のラベル: N手目（検討開始局面からの手数）。 */
    fun studyPlyLabel(ply: Int): String = "検討${ply}手目"

    // 検討モードの評価エラーはナビ行に evalSuffix(EVAL_UNAVAILABLE) で表示する。


    const val STUDY_PANEL_TITLE = "検討中"

    /** 分岐元行。label = 分岐元の手＋形勢（例:「42手目 ▲３四飛（−320）」）。 */
    fun studyOriginLine(label: String): String = "${label}から分岐"

    /** 評価スロットの手動リトライボタンのラベル。 */
    const val STUDY_ANALYZE_LABEL = "解析"

    /**
     * ローカルエンジンが使える見込みが無く自動発火を保留中の文言。解析中表示と紛れないよう、
     * 「解析していない・準備段階」であることを明示する語にする（WASMバイナリのダウンロード
     * 未完・サーバーフォールバック中のどちらの原因でも成立する言い回し）。
     */
    const val STUDY_EVAL_PREPARING = "解析の準備中"

    /** 評価スロットの解析中表示。 */
    const val STUDY_EVAL_ANALYZING = "解析中"

    /** 評価スロットの最善手表示（例:「最善 ▲2六歩」）。moveText は棋譜表記済みの文字列。 */
    fun studyBestMoveLabel(moveText: String): String = "最善 $moveText"

    fun studyChipEvalSuffix(text: String): String = "($text)"

    const val STUDY_BRANCH_CURRENT_SUFFIX = "（いま）"

    const val STUDY_BRANCH_ICON_DESC = "他の変化があります"

    /** 兄弟変化ポップメニューで、その変化がまだ未解析のときの評価欄プレースホルダー。 */
    const val STUDY_BRANCH_EVAL_UNKNOWN = "—"

    /** 通常モードのナビ行中央（現在手表示）に付ける、棋譜リストへのタップ導線ヒント。 */
    const val MOVE_LIST_DROPDOWN_HINT = " ▾"

    // トップバーは32dpの1行インライン行（MainActivity.kt ReportScreen）。
    // 棋戦名使用時のファイル名は3行目として表示せず、Info アイコンの
    // 対局情報ダイアログに集約する。

    /** トップバー: 対局情報アイコンの contentDescription。 */
    const val GAME_INFO_ICON_DESC = "対局情報"
    /** 対局情報ダイアログのタイトル。 */
    const val GAME_INFO_DIALOG_TITLE = "この棋譜について"
    /** 対局情報ダイアログの閉じるボタン。 */
    const val GAME_INFO_CLOSE = "閉じる"

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


    /** 401: セッション再取得（AuthRetryingAnalyzer）を試みても解決しなかった場合。 */
    const val SERVER_ANALYSIS_ERROR_UNAUTHORIZED = "セッションの更新に失敗しました。時間をおいてお試しください"
    const val SERVER_ANALYSIS_ERROR_APP_CHECK =
        "アプリの検証に失敗しました。App Storeの最新版でお試しください"

    /** 403: user_bans 登録済み（BAN）。 */
    const val SERVER_ANALYSIS_ERROR_BANNED = "このアカウントはサーバー解析を利用できません"

    /** 429: 当日クォータ超過。resetAtJst = [dev.miyado.shogisupplement.util.formatResetAtJst] 整形済み文字列。 */
    fun serverAnalysisErrorQuotaExceeded(resetAtJst: String): String =
        "本日のサーバー解析の上限に達しました。${resetAtJst}にリセットされます"

    /** 400: リクエスト不正（想定外）。 */
    const val SERVER_ANALYSIS_ERROR_BAD_REQUEST = "解析リクエストが不正です。時間をおいてお試しください"

    /** サーバー側エンジン失敗（NDJSON終端の error 行）。 */
    const val SERVER_ANALYSIS_ERROR_ENGINE_FAILURE = "サーバー側の解析に失敗しました。時間をおいてお試しください"

    /** 再POSTの上限回数に達しても復旧できなかった接続断。 */
    const val SERVER_ANALYSIS_ERROR_CONNECTION_LOST = "サーバーへの接続が回復しませんでした。ネットワークを確認してお試しください"

    // ═══ 31. 同意オンボーディング（iOS専用・初回起動必須）═══════════════════════

    const val CONSENT_TITLE = "はじめに"
    const val CONSENT_INTRO =
        "将棋サプリは棋譜を解析して棋力を推定し、棋力帯相応の悪手を次の一手問題として出題します。"

    const val CONSENT_WITH_ACCOUNT_LABEL = "匿名のアカウントを作成して始める"
    val CONSENT_WITH_ACCOUNT_POINTS = listOf(
        "ベータ版では1日30局まで早く解析できます。30局を超えると解析が遅くなります",
        "サーバーに棋譜を保存すると、機種変更のときに棋譜を引き継げます",
        "名前・メールアドレスの入力は必要ありません",
        "サーバーに保存した棋譜と結果は匿名で研究・改善（アプリ改善・棋力推定の較正）に利用されます",
    )

    const val CONSENT_WITHOUT_ACCOUNT_LABEL = "アカウントを作成せずに始める"
    val CONSENT_WITHOUT_ACCOUNT_POINTS = listOf(
        "解析は端末内で行います。棋譜はサーバーに送信されません",
    )

    const val CONSENT_ACCEPT_BUTTON = "規約に同意して始める"

    // ═══ 32. 引き継ぎコード表示・入力（設定画面）══════════════════════════════════

    const val TRANSFER_CODE_TITLE = "引き継ぎコード"
    const val SETTINGS_ROW_TRANSFER_CODE = "引き継ぎコード"
    const val SETTINGS_ROW_TRANSFER_CODE_SUB = "別の端末への引き継ぎに使うコードを表示"
    const val TRANSFER_CODE_DESCRIPTION =
        "このコードで別の端末へ引き継げます。他人に知られると棋譜とアカウントにアクセスされます。" +
        "画面の写真やメモアプリなど、安全な場所に控えてください。"
    const val TRANSFER_CODE_COPY_BUTTON = "コピー"
    const val TRANSFER_CODE_COPIED = "コピーしました"
    const val TRANSFER_CODE_REGENERATE_BUTTON = "コードを作り直す"
    const val TRANSFER_CODE_REGENERATE_DIALOG_TITLE = "コードを作り直しますか？"
    const val TRANSFER_CODE_REGENERATE_DIALOG_TEXT =
        "新しいコードを発行し、いまのコードは使えなくなります。" +
        "サーバーに保存した棋譜はそのまま引き継げます。"
    const val TRANSFER_CODE_REGENERATE_CONFIRM = "作り直す"
    const val TRANSFER_CODE_REGENERATE_FAILED = "作り直しに失敗しました。時間をおいてお試しください"

    const val TRANSFER_CODE_REVEAL_ICON_DESC = "コードを表示"
    const val TRANSFER_CODE_HIDE_ICON_DESC = "コードを隠す"

    const val SETTINGS_ROW_TRANSFER_CODE_INPUT = "引き継ぎコードを入力"
    const val SETTINGS_ROW_TRANSFER_CODE_INPUT_SUB = "別の端末で発行したコードでこの端末を復元"
    const val TRANSFER_CODE_INPUT_TITLE = "引き継ぎコードを入力"
    const val TRANSFER_CODE_INPUT_DESCRIPTION = "別の端末の設定→引き継ぎコードで表示したコードを入力してください。"
    const val TRANSFER_CODE_INPUT_FIELD_LABEL = "コード"
    const val TRANSFER_CODE_INPUT_SUBMIT = "復元する"
    const val TRANSFER_CODE_INPUT_SUCCESS = "アカウントを切り替えました"
    const val TRANSFER_CODE_INPUT_SUCCESS_CLOSE = "閉じる"
    const val TRANSFER_CODE_INPUT_CONFIRM_TITLE = "アカウントを切り替えますか？"
    const val TRANSFER_CODE_INPUT_CONFIRM_TEXT =
        "この端末は入力したコードのアカウントに切り替わります。今のアカウントのデータはサーバーに残り、消えません。"
    const val TRANSFER_CODE_INPUT_CONFIRM_BUTTON = "切り替える"
    const val TRANSFER_CODE_INPUT_ERROR_INVALID = "コードが正しくありません。入力し直してください。"
    const val TRANSFER_CODE_INPUT_ERROR_NOT_FOUND = "一致するコードが見つかりません。コードを確認してください。"
    const val TRANSFER_CODE_INPUT_ERROR_RATE_LIMITED = "しばらく時間をおいて再度お試しください。"
    const val TRANSFER_CODE_INPUT_ERROR_UPGRADE_REQUIRED = "アップデートが必要です。最新版に更新してから再度お試しください。"
    const val TRANSFER_CODE_INPUT_ERROR_GENERIC = "通信に失敗しました。しばらくしてから再度お試しください。"

    // ═══ 33. 評価値グラフ・悪手率・エンジン一致率（レポート画面）══════════════════

    /** 評価値グラフのカード見出し。ユーザーの先後で符号を反転済み（自分視点で統一）であることを明示する。 */
    const val EVAL_GRAPH_TITLE = "形勢の推移（自分視点）"

    /** 悪手率行のラベル（値は Mono で続く）。分母は MATCH_RATE_LABEL と同じ n（自分の手数）。 */
    const val BLUNDER_RATE_LABEL = "悪手率: "

    /** 悪手率の値表示。例: pct=12, m=3, n=25 → "12%(3/25)" */
    fun blunderRateValue(pct: Int, m: Int, n: Int): String = "${pct}%(${m}/${n})"

    /** エンジン一致率行のラベル（値は Mono で続く）。 */
    const val MATCH_RATE_LABEL = "一致率（最善・次善）: "

    /** エンジン一致率の値表示。例: pct=62, l=31, n=50 → "62%(31/50)" */
    fun matchRateValue(pct: Int, l: Int, n: Int): String = "${pct}%(${l}/${n})"

    // ═══ 34. レポート画面: サマリー/悪手一覧の切替 ══════════════════════════════
    // 既定表示はグラフ＋サマリー。悪手（グラフの朱マーカー）を選ぶか、
    // 「悪手一覧を見る」で悪手一覧（既存のカードリスト）に切り替わる。

    const val VIEW_BLUNDER_LIST = "悪手一覧を見る"

    /** 悪手一覧からサマリーへ戻るボタンの表示文言・contentDescription 兼用。 */
    const val BACK_TO_SUMMARY = "サマリーに戻る"

    // ═══ 35. Web検討ページ（:webApp）═════════════════════════════════════════

    const val KENTO_TITLE = "棋譜を検討する"
    const val KENTO_ASSETS_UNAVAILABLE = "この機能は準備中です。しばらくしてからもう一度お試しください。"

    const val KENTO_KIF_NOTE = "対局サイト・アプリの棋譜コピー機能で取得したKIF形式のテキストを貼り付けてください（平手のみ対応）。"
    const val KENTO_KIF_PLACEHOLDER = "対局サイト・アプリの「棋譜コピー」等で取得したKIFテキストをここに貼り付け"
    const val KENTO_PRIVACY_NOTE = "解析はお使いの端末内で完結し、棋譜が送信されることはありません"
    const val KENTO_ANALYZE_BUTTON = "解析開始"
    const val KENTO_CANCEL_BUTTON = "キャンセル"

    /** 解析中の進捗表示。done/total は局面数。 */
    fun kentoAnalyzing(done: Int, total: Int): String = "解析中... $done/${total}局面"

    /** WASMバイナリの初回ダウンロードは数分かかりうるため、その旨を明示する補足。 */
    const val KENTO_ENGINE_DOWNLOAD_NOTE = "初回は解析エンジンのダウンロードに時間がかかります"

    /** KIFの場所ヘッダが無い棋譜でレポート画面タイトルになる語。 */
    const val KENTO_PASTED_GAME_TITLE = "貼り付けた棋譜"

    const val KENTO_ERROR_GENERIC = "エラーが発生しました。ページを再読み込みしてからもう一度お試しください。"
    const val KENTO_ERROR_EMPTY_INPUT = "入力が空です"
    const val KENTO_ERROR_KIF_PARSE = "KIFの解析に失敗しました"
    const val KENTO_ERROR_NO_MOVES = "指し手が0件です"

    // ═══ 36. 強制アップデート ════════════════════════════════════════════════
    // 「強制」「ブロック」は内部の実装用語のため、ここから先のユーザー向け文言には出さない
    // （docs/wording.md参照）。全画面ブロック・閉じる導線なし（ForceUpdateScreen）。
    // 判定ロジックは dev.miyado.shogisupplement.policy.ForceUpdateJudge（:analysis）。

    const val FORCE_UPDATE_TITLE = "アップデートが必要です"
    const val FORCE_UPDATE_BODY = "最新版でのみご利用いただけます。ストアからアップデートしてください。"
    const val FORCE_UPDATE_NOTE =
        "棋譜と解析結果は端末に残っています。アップデート後もそのままご利用いただけます。"
    const val FORCE_UPDATE_OPEN_STORE = "ストアを開く"

    /** バージョン表示行のラベル部分（値=versionName+ビルド番号はMonoで別Text）。 */
    const val FORCE_UPDATE_VERSION_PREFIX = "お使いのバージョン "

    /** バージョン表示の値部分。例: versionName="1.2.0", buildNumber=42 → "1.2.0 (42)" */
    fun forceUpdateVersionValue(versionName: String, buildNumber: Int): String = "$versionName ($buildNumber)"

    // ═══ 37. 棋譜復元（引き継ぎコード復元後の棋譜ダウンロード）═══════════════════════
    // 見出し文言は TRANSFER_CODE_INPUT_SUCCESS（アカウントを切り替えました）を再利用する
    // （同じ意味の文言をここで別に持たない）。ホームへ戻るボタンは DRILL_GO_HOME を再利用する。

    const val GAME_RESTORE_LOADING_NOTE = "サーバー上の棋譜を確認しています"

    /** サーバー上の自分の棋譜件数の案内。0件は [GAME_RESTORE_EMPTY_NOTE] を使う。 */
    fun gameRestoreCount(count: Int): String = "サーバーに $count 局の棋譜があります。"

    const val GAME_RESTORE_EMPTY_NOTE = "サーバーに保存された棋譜はありません。"
    const val GAME_RESTORE_BUTTON = "棋譜を復元する"
    const val GAME_RESTORE_ANALYZE_BUTTON = "復元した棋譜を解析する"

    /**
     * ダウンロード進捗（進捗スロット表示・全体をMono表記）。
     * AnalyzingProgress と同じ「ラベル込み1文をMono表示」パターン（AnalyzingReportScreen参照）。
     * 例: done=3, total=10 → "復元中... 3 / 10 局"
     */
    fun gameRestoreProgress(done: Int, total: Int): String = "復元中... $done / $total 局"

    /** 完了時（全件成功）。 */
    fun gameRestoreCompletedAll(succeeded: Int): String = "$succeeded 局を復元しました。"

    /**
     * 完了時（一部失敗）。色分け（DESIGN.md「数値の符号規約: 損失・悪化=朱」）は文言に含めず、
     * 表記自体は中立に保つ。
     */
    fun gameRestoreCompletedPartial(succeeded: Int, failed: Int): String =
        "$succeeded 局を復元しました（$failed 局は失敗）。"

    const val GAME_RESTORE_ERROR_NOT_AUTHENTICATED = "ログイン状態を確認できませんでした。設定からやり直してください。"
    const val GAME_RESTORE_ERROR_NO_SECRET = "復元用のデータが見つかりませんでした。もう一度お試しください。"
    const val GAME_RESTORE_ERROR_NETWORK = "棋譜を取得できませんでした。しばらくしてから再度お試しください。"
    const val GAME_RESTORE_RETRY_BUTTON = "再試行"

    /** ダウンロード復元した棋譜のfileName。開始日時ヘッダがあればそれを使い、無ければ汎用ラベル。 */
    fun restoredGameFileName(startedAt: String?): String = startedAt ?: "復元した棋譜"

    // ═══ 38. 推定棋力詳細ページ（ホーム画面の推定棋力カードタップで遷移）═══════════════

    const val STRENGTH_DETAIL_TITLE = "推定棋力"
    const val STRENGTH_DETAIL_EYEBROW = "現在の推定棋力"

    /** 推定範囲（偏差値の下限・上限）。例: low=54, high=62 → "推定範囲 54–62" */
    fun strengthDetailRange(low: Int, high: Int): String = "推定範囲 $low–$high"

    /** 対局サービス側の申告段級位のうち最も高いものの見出し（このページだけの要約値。内部の偏差値とは別物）。 */
    const val STRENGTH_DETAIL_BEST_RANK_CAPTION = "対局サービスでの最高段級位"

    /** 例: service="将棋ウォーズ", rank="初段" → "将棋ウォーズ 初段" */
    fun strengthDetailBestRankValue(serviceLabel: String, rankLabel: String): String = "$serviceLabel $rankLabel"

    const val STRENGTH_DETAIL_TREND_TITLE = "対局ごとの推移"

    /** 例: count=8 → "直近8局" */
    fun strengthDetailTrendLabel(count: Int): String = "直近${count}局"

    const val STRENGTH_DETAIL_TREND_CAPTION = "帯は推定範囲です。対局数が増えると範囲が安定します。"

    /**
     * 選択中の対局の要約行（グラフの点タップで切り替わる）。
     * [BLUNDER_RATE_LABEL]・[MATCH_RATE_LABEL]は「ラベル: 値」の1行表示用のため、
     * 2指標を並べるこの用途にはコロン無しの短いラベルを別に持つ。
     */
    fun strengthDetailSelectedMeta(blunderRateValue: String, matchRateValue: String): String =
        "悪手率 $blunderRateValue ・ 一致率 $matchRateValue"

    const val STRENGTH_DETAIL_ACCOUNTS_TITLE = "対局サービス"
    const val STRENGTH_DETAIL_ACCOUNTS_LEDE = "アカウントを登録すると、先後の自動判定や段級位の記録に使われます。"
    const val STRENGTH_DETAIL_ACCOUNTS_EMPTY = "まだ何も入力されていません。"
    const val STRENGTH_DETAIL_ACCOUNTS_EDIT = "編集"
    const val STRENGTH_DETAIL_ACCOUNT_NAME_UNSET = "アカウント名未入力"
    const val STRENGTH_DETAIL_RULE_UNSET = "未入力"
    const val STRENGTH_DETAIL_LISHOGI_RATING_LABEL = "レーティング"
}
