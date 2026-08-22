import XCTest

/// App Store提出用スクリーンショットの撮影バッチ（1.1 UI版）。
///
/// フローは PasteboardImportSmokeTests.swift と同一（クリップボード取込→先後選択→
/// 解析完了待ち→レポート自動遷移）を土台にする。1.1のレポート画面はデフォルトで
/// 評価値グラフ＋サマリーを表示し、「悪手一覧を見る」で一覧に切替、盤面（駒のあるマス）を
/// タップすると検討モード（分岐ツリー）に入れる。実行後は xcresulttool で xcresult から
/// PNG を抽出する（抽出スクリプト側の作業。本ファイルは撮影のみを担う）。
///
/// 撮影する7画面（ファイル名の番号は最終成果物の並び。撮影自体はロジック上たどりやすい
/// 順序で行うため番号順とは限らない）:
///   1. 01_home            — ホーム（推定棋力カード＋今日の1問＋解析済み棋譜）
///   2. 02_report_graph    — レポート（デフォルト表示＝評価値グラフ＋サマリー）
///   3. 03_blunder_list    — 「悪手一覧を見る」→ 悪手一覧（本譜/最善の変化タブが見える状態）
///   4. 04_study           — 盤面タップ→検討モード開始（分岐ツリーの検討パネル）
///   5. 05_drill_question  — 次の一手問題の出題画面（盤面＋降参ボタン）
///   6. 06_drill_result    — 降参して答えを見た結果画面
///   7. 07_report_dark     — レポート（02と同内容）のダークモード。
///      XCUIDevice.appearance = .dark を実測したところ Compose Multiplatform 側の
///      isSystemInDarkTheme() には反映されず（ShogiTheme既定の themeMode="system"
///      経由では追従しなかった）、アプリ内の設定画面「テーマ」→「ダーク」を明示選択する
///      方式に変更した。この経路は永続化される themeMode を直接 "dark" にするため
///      OS外観に依存せず確実（Theme.kt参照）。プロセス再起動も不要（ライブに再描画される）。
final class StoreScreenshotTests: XCTestCase {

    private var app: XCUIApplication!
    private var pasteInterruptionMonitor: NSObjectProtocol?

    /// ペースト許可アラートの文言候補（iOSバージョン差・言語差を吸収）。
    private static let allowPasteLabels = [
        "ペーストを許可", "許可", "貼り付けを許可", "ペースト",
        "Allow Paste", "Allow", "OK",
    ]

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    override func tearDownWithError() throws {
        if let monitor = pasteInterruptionMonitor {
            removeUIInterruptionMonitor(monitor)
            pasteInterruptionMonitor = nil
        }
    }

    func testCaptureStoreScreenshots() throws {
        let kifText = try loadFixtureKifText()

        app = XCUIApplication()

        pasteInterruptionMonitor = addUIInterruptionMonitor(withDescription: "ペースト許可アラート") { alert in
            for label in Self.allowPasteLabels {
                let button = alert.buttons[label]
                if button.exists {
                    button.tap()
                    return true
                }
            }
            return false
        }

        // KIFはlaunchEnvironmentで渡し、アプリ自身にクリップボードへ書き込ませる
        // （iosAppApp.swift の seedPasteboardForUITestIfNeeded 参照）。
        app.launchEnvironment["UITEST_PASTEBOARD_KIF_BASE64"] = Data(kifText.utf8).base64EncodedString()
        // ContentView.swift の開発用タブバー（Spike/CMP）は撮影に写り込むと店頭に出せないため、
        // 撮影中だけ隠す（Releaseと同じ「ComposeViewが画面全体」の見た目にする）。
        // launchEnvironmentはXCUIApplicationインスタンスに残るため、後段のダークモード
        // 再起動（app.launch()の2回目）にも自動的に引き継がれる。
        app.launchEnvironment["UITEST_HIDE_DEBUG_TABS"] = "1"
        app.launch()

        // 同意オンボーディング（[ConsentScreen]）は初回起動のみ表示される
        // （PasteboardImportSmokeTestsと同一）。フレッシュなシミュレータでは必ず出る。
        handleConsentOnboardingIfNeeded()

        // ── 1. 棋譜取込フロー（PasteboardImportSmokeTestsと同一） ──────────────
        let addKifButton = element(labeled: "棋譜を追加する", timeout: 20)
        XCTAssertTrue(addKifButton.exists, "「棋譜を追加する」ボタンが見つかりません")
        addKifButton.tap()

        let clipboardOption = element(labeled: "クリップボードから貼り付け", timeout: 10)
        XCTAssertTrue(clipboardOption.exists, "「クリップボードから貼り付け」が見つかりません")
        clipboardOption.tap()

        handlePastePermissionAlertIfNeeded()

        // フレッシュな端末（アカウント名未設定）では先後選択の前に棋力設定ダイアログ
        // （ImportState.RatingSetup → RatingSettingsDialog）が先に表示される。
        // 重要: アカウント名を空のまま「保存」すると hasAnyServiceAccount() が false のままで
        // completeRatingSetup → proceedAfterKifValidated が RatingSetup を再表示し、
        // ダイアログが閉じないように見える（IosMainController.kt:235）。フィクスチャの
        // 後手名 "miyado" をアカウント名として入力してから保存する（先後自動サジェストも
        // 後手側に効く）。設定済みの端末（再実行時）では表示されないため、出現は条件付き。
        let ratingDialogTitle = element(labeled: "棋力設定", timeout: 8)
        if ratingDialogTitle.exists {
            // アカウント名フィールド: ラベル「アカウント名（…）」のStaticTextを内包するTextView
            let accountFieldPredicate = NSPredicate(format: "label CONTAINS %@", "アカウント名")
            let accountField = app.textViews
                .containing(accountFieldPredicate)
                .firstMatch
            XCTAssertTrue(
                accountField.waitForExistence(timeout: 5),
                "棋力設定ダイアログのアカウント名フィールドが見つかりません",
            )
            accountField.tap()
            accountField.typeText("miyado")

            // 保存 → ダイアログが閉じるまで確認（閉じなければ再タップ・最大3回）
            var ratingDialogClosed = false
            for _ in 0..<3 {
                let saveButton = app.buttons["保存"].firstMatch
                XCTAssertTrue(saveButton.waitForExistence(timeout: 5), "棋力設定ダイアログの「保存」が見つかりません")
                saveButton.tap()
                if waitForDisappearance(of: ratingDialogTitle, timeout: 5) {
                    ratingDialogClosed = true
                    break
                }
            }
            XCTAssertTrue(ratingDialogClosed, "棋力設定ダイアログが閉じません（アカウント名の入力に失敗した可能性）")
        }

        let goteOption = element(labeledStartsWith: "後手（", timeout: 15)
        XCTAssertTrue(goteOption.exists, "先後選択ダイアログ（後手）が見つかりません")
        goteOption.tap()

        let startButton = element(labeled: "解析開始", timeout: 5)
        XCTAssertTrue(startButton.exists, "「解析開始」ボタンが見つかりません")
        startButton.tap()

        let dialogTitle = element(labeled: "自分の側", timeout: 2)
        XCTAssertTrue(
            waitForDisappearance(of: dialogTitle, timeout: 10),
            "先後選択ダイアログが閉じません（ラジオ選択が反映されていない可能性）",
        )

        // ── 2. 解析完了 → レポート画面へ自動遷移（最大10分待機） ─────────────────
        // 1.1のデフォルト表示は評価値グラフ＋サマリー（悪手一覧はまだ出ていない）。
        let reportIndicator = element(
            labeledContainsAny: ["この一局からの推定棋力", "悪手は見つかりませんでした"],
            timeout: 600,
        )
        XCTAssertTrue(reportIndicator.exists, "レポート画面へ自動遷移しませんでした（10分待機）")

        // 描画安定化のための小休止（初回フレームのちらつき対策）。
        Thread.sleep(forTimeInterval: 1.5)
        attachScreenshot(named: "02_report_graph")

        // ── 3. 盤面タップ2回で検討モード開始（04_study） ───────────────────────
        // SUMMARYのデフォルト表示は ply=0（開始局面・先手番）。フィクスチャの本譜そのもの
        // （初手 ７六歩=7g7f・2手目 ３四歩=3c3d）を検討ラインとして指す——常に合法・
        // 成り判定不要なため自動化として確実（ReportBoardArea/StudyController参照。
        // 盤マスの識別子は ShogiBoardView.kt の testTag("board_sq_<file>_<rank>")、
        // iOSでは testTag がそのまま accessibilityIdentifier として露出する
        // （app/docs/e2e-testing.md）。
        let sq77 = elementWithIdentifier("board_sq_7_7", timeout: 10)
        XCTAssertTrue(sq77.exists, "盤面のマス(7,7)が見つかりません")
        sq77.tap()
        Thread.sleep(forTimeInterval: 0.3)

        let sq76 = elementWithIdentifier("board_sq_7_6", timeout: 5)
        XCTAssertTrue(sq76.exists, "盤面のマス(7,6)が見つかりません")
        sq76.tap()

        let studyTitle = element(labeled: "検討中", timeout: 5)
        XCTAssertTrue(studyTitle.exists, "検討モードへ切り替わりませんでした")

        // 2手目（後手番になった検討局面での合法手）も指し、分岐ツリーに手順チップが
        // 複数並ぶ状態にする。
        let sq33 = elementWithIdentifier("board_sq_3_3", timeout: 5)
        XCTAssertTrue(sq33.exists, "盤面のマス(3,3)が見つかりません")
        sq33.tap()
        Thread.sleep(forTimeInterval: 0.3)

        let sq34 = elementWithIdentifier("board_sq_3_4", timeout: 5)
        XCTAssertTrue(sq34.exists, "盤面のマス(3,4)が見つかりません")
        sq34.tap()

        Thread.sleep(forTimeInterval: 0.8)
        attachScreenshot(named: "04_study")

        // 検討終了 → 元のサマリー表示へ戻す（ReportScreen.exitStudy）。
        let endButton = element(labeled: "終了", timeout: 5)
        XCTAssertTrue(endButton.exists, "検討モードの「終了」ボタンが見つかりません")
        endButton.tap()
        XCTAssertTrue(waitForDisappearance(of: studyTitle, timeout: 5), "検討モードが終了しませんでした")

        // ── 4. 「悪手一覧を見る」→ 悪手一覧（03_blunder_list） ─────────────────
        let viewListButton = element(labeled: "悪手一覧を見る", timeout: 5)
        XCTAssertTrue(viewListButton.exists, "「悪手一覧を見る」ボタンが見つかりません")
        viewListButton.tap()

        let mainlineTab = element(labeled: "本譜", timeout: 5)
        XCTAssertTrue(mainlineTab.exists, "悪手一覧の「本譜」タブが見つかりません")

        // 先頭の悪手カードをタップして選択状態にする（タブ自体は選択前から見えているが、
        // カード選択済みの方が画面として自然なため）。悪手カードは「実戦」ラベル
        // （BLUNDER_CARD_ACTUAL）を目印にする（GameCardと同じ座標ヒット方式）。
        let actualLabelPredicate = NSPredicate(format: "label CONTAINS %@", "実戦")
        let firstBlunderCard = app.descendants(matching: .any).matching(actualLabelPredicate).firstMatch
        if firstBlunderCard.waitForExistence(timeout: 5) {
            firstBlunderCard.tap()
        }
        Thread.sleep(forTimeInterval: 0.8)
        attachScreenshot(named: "03_blunder_list")

        // ── 5. ホームへ戻る（01_home） ──────────────────────────────────────
        let backButton = element(labeled: "戻る", timeout: 5)
        XCTAssertTrue(backButton.exists, "レポート画面の「戻る」ボタンが見つかりません")
        backButton.tap()

        let homeAgain = element(labeled: "棋譜を追加する", timeout: 15)
        XCTAssertTrue(homeAgain.exists, "ホームへ戻れませんでした")
        Thread.sleep(forTimeInterval: 1.0)
        attachScreenshot(named: "01_home")

        // ── 6. 「今日の1問」→ 次の一手問題の出題画面（05_drill_question） ──────
        let todaysDrill = element(labeledContainsAny: ["今日の1問", "次の一手"], timeout: 10)
        XCTAssertTrue(todaysDrill.exists, "「今日の1問」（または次の一手導線）が見つかりません")
        todaysDrill.tap()

        let drillGiveUp = element(labeled: "答えを見る", timeout: 15)
        XCTAssertTrue(drillGiveUp.exists, "次の一手問題の出題画面に到達しませんでした")
        Thread.sleep(forTimeInterval: 0.8)
        attachScreenshot(named: "05_drill_question")

        // ── 7. 降参 → 次の一手問題の結果画面（06_drill_result） ─────────────
        drillGiveUp.tap()

        let drillResult = element(labeledContainsAny: ["正解。", "不正解"], timeout: 15)
        XCTAssertTrue(drillResult.exists, "次の一手問題の結果画面に到達しませんでした")
        Thread.sleep(forTimeInterval: 0.8)
        attachScreenshot(named: "06_drill_result")

        // ── 8. ダークモードでレポート画面を撮る（07_report_dark） ──────────────
        // XCUIDevice.appearance = .dark は実測でCompose側のisSystemInDarkTheme()に
        // 反映されなかった（ヘッダコメント参照）ため、アプリ内の設定「テーマ」→「ダーク」を
        // 明示選択する（themeModeを直接 "dark" にする・OS外観非依存で確実）。

        let goHomeButton = element(labeled: "ホームへ", timeout: 5)
        XCTAssertTrue(goHomeButton.exists, "次の一手問題の結果画面の「ホームへ」ボタンが見つかりません")
        goHomeButton.tap()

        let homeAfterDrill = element(labeled: "棋譜を追加する", timeout: 10)
        XCTAssertTrue(homeAfterDrill.exists, "ホームへ戻れませんでした")

        let settingsIcon = element(labeled: "設定", timeout: 5)
        XCTAssertTrue(settingsIcon.exists, "設定アイコンが見つかりません")
        settingsIcon.tap()

        let themeRow = element(labeled: "テーマ", timeout: 5)
        XCTAssertTrue(themeRow.exists, "設定画面の「テーマ」行が見つかりません")
        themeRow.tap()

        let darkOption = element(labeled: "ダーク", timeout: 5)
        XCTAssertTrue(darkOption.exists, "テーマ選択ダイアログの「ダーク」が見つかりません")
        darkOption.tap()

        // ダイアログを閉じてダーク配色に切り替える処理はComposeの状態更新のみ（DB/ネットワーク
        // 待ちが無い）ため短い小休止で足りる。「ダーク」ラベルでの消失待ちは使わない——
        // ダイアログを閉じた直後、設定行自体のsub表示（AppStrings.themeLabel）も同じ文言
        // 「ダーク」になり、消失を検出できなくなるため。
        Thread.sleep(forTimeInterval: 0.8)

        let settingsBackButton = element(labeled: "戻る", timeout: 5)
        XCTAssertTrue(settingsBackButton.exists, "設定画面の「戻る」ボタンが見つかりません")
        settingsBackButton.tap()

        // ホームの棋譜カードのタイトルは sourcePlace（KIFの「場所」ヘッダ＝将棋ウォーズ）
        // から決まる表示ラベル（AppStrings.sourcePlaceLabel）。クリップボード取込では
        // ファイル名が "クリップボード <日時>" になり毎回変わるため、固定文字列の
        // 「将棋ウォーズ」で探す。
        let gameCard = element(labeled: "将棋ウォーズ", timeout: 15)
        XCTAssertTrue(gameCard.exists, "ホームの棋譜カード（将棋ウォーズ）が見つかりません")
        gameCard.tap()

        let reportIndicatorDark = element(
            labeledContainsAny: ["この一局からの推定棋力", "悪手は見つかりませんでした"],
            timeout: 30,
        )
        XCTAssertTrue(reportIndicatorDark.exists, "ダークモードでレポート画面へ遷移しませんでした")
        Thread.sleep(forTimeInterval: 1.5)
        attachScreenshot(named: "07_report_dark")
    }

    // MARK: - Helpers（PasteboardImportSmokeTests.swiftと同一実装。詳細コメントは同ファイル参照）

    /// 同意オンボーディング画面（[ConsentScreen]）が出ていれば、研究利用チェックをON→
    /// 「同意して始める」で確定する。同意確定処理（匿名サインイン等）は非同期のため、
    /// 画面が閉じる（ホームへ遷移する）までタイムアウト長めに待つ。
    private func handleConsentOnboardingIfNeeded() {
        let consentTitle = element(labeled: "はじめに", timeout: 5)
        guard consentTitle.exists else { return }

        let consentCheckbox = element(
            labeledContainsAny: ["解析した棋譜と結果を匿名で研究利用"],
            timeout: 5,
        )
        XCTAssertTrue(consentCheckbox.exists, "同意オンボーディングの研究利用チェック項目が見つかりません")
        consentCheckbox.tap()

        let acceptButton = element(labeled: "同意して始める", timeout: 5)
        XCTAssertTrue(acceptButton.exists, "「同意して始める」ボタンが見つかりません")
        acceptButton.tap()

        XCTAssertTrue(
            waitForDisappearance(of: consentTitle, timeout: 30),
            "同意オンボーディング画面が閉じません（匿名サインイン待ち含む）",
        )
    }

    private func loadFixtureKifText() throws -> String {
        let bundle = Bundle(for: Self.self)
        guard let url = bundle.url(forResource: "wars_game3", withExtension: "kif") else {
            throw XCTSkip("フィクスチャ wars_game3.kif がUIテストバンドルに見つかりません")
        }
        return try String(contentsOf: url, encoding: .utf8)
    }

    private func handlePastePermissionAlertIfNeeded(timeout: TimeInterval = 8) {
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            for label in Self.allowPasteLabels {
                let springboardButton = springboard.buttons[label]
                if springboardButton.exists {
                    springboardButton.tap()
                    return
                }
                let appButton = app.buttons[label]
                if appButton.exists {
                    appButton.tap()
                    return
                }
            }
            usleep(300_000)
        }
    }

    private func element(labeled label: String, timeout: TimeInterval) -> XCUIElement {
        let predicate = NSPredicate(format: "label == %@", label)
        let el = app.descendants(matching: .any).matching(predicate).firstMatch
        _ = el.waitForExistence(timeout: timeout)
        return el
    }

    private func element(labeledStartsWith prefix: String, timeout: TimeInterval) -> XCUIElement {
        let predicate = NSPredicate(format: "label BEGINSWITH %@", prefix)
        let el = app.descendants(matching: .any).matching(predicate).firstMatch
        _ = el.waitForExistence(timeout: timeout)
        return el
    }

    /// 盤面マス等、accessibilityIdentifier（Compose testTag）で探す版。
    /// label（可視テキスト）が無い要素向け（board_sq_<file>_<rank>）。
    private func elementWithIdentifier(_ identifier: String, timeout: TimeInterval) -> XCUIElement {
        let predicate = NSPredicate(format: "identifier == %@", identifier)
        let el = app.descendants(matching: .any).matching(predicate).firstMatch
        _ = el.waitForExistence(timeout: timeout)
        return el
    }

    private func waitForDisappearance(of element: XCUIElement, timeout: TimeInterval) -> Bool {
        let predicate = NSPredicate(format: "exists == false")
        let expectation = XCTNSPredicateExpectation(predicate: predicate, object: element)
        return XCTWaiter().wait(for: [expectation], timeout: timeout) == .completed
    }

    private func element(labeledContainsAny substrings: [String], timeout: TimeInterval) -> XCUIElement {
        let subpredicates = substrings.map { NSPredicate(format: "label CONTAINS %@", $0) }
        let predicate = NSCompoundPredicate(orPredicateWithSubpredicates: subpredicates)
        let el = app.descendants(matching: .any).matching(predicate).firstMatch
        _ = el.waitForExistence(timeout: timeout)
        return el
    }

    private func attachScreenshot(named name: String) {
        let screenshot = XCUIScreen.main.screenshot()
        let attachment = XCTAttachment(screenshot: screenshot)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
