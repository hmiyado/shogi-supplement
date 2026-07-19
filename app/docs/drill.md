# ドリル機能

## 出題

- 出題候補は DB の blunder_report のうち判定が ◎（優先出題）または ○（出題対象）のもの。
  △見送りは出題しない（出題ポリシー: 帯相応のミスこそ訓練対象）
- 次の1問の決定則（`shared/drill/DrillRotation.selectNext()`）:
  1. 解答回数が少ない問題を優先（drill_attempt 件数が最小のもの）
  2. 同数なら ◎ → ○ の順（verdict 先頭文字で比較）
  3. さらに同数なら priority 降順
- この決定則により、状態を別途保存しなくても全問を1周ずつ自然に消化できる。
  全問の解答回数が同数になったとき（＝1周完了）、次の周が自然に始まる

## 正誤判定（`shared/drill/DrillJudge.kt`）

- **正解基準: 最善手との loss_wp 差 ≤ 0.05（勝率5%）**
- 閾値定数: `DrillJudge.CORRECT_LOSS_WP_THRESHOLD`（ここを変えれば全体に効く）
- 判定手順: best_usi 一致→即正解 ／ 実戦悪手一致→即不正解 ／ それ以外→エンジン2局面解析
  （出題局面の最善評価値と、ユーザー手後の相手番評価値から loss_wp を計算）
- エンジンは `(sfen) -> List<PvInfo>` の関数注入。テストは fake で JVM 完結
  （`shared/src/jvmTest/.../drill/DrillJudgeTest.kt`）

## 解答履歴

- drill_attempt テーブル（blunder_report_id / user_move_usi / is_correct / loss_wp / attempted_at）。
  降参は user_move_usi="[降参]" で、解答1回としてカウントされる。
  将来の間隔反復はこのテーブルが基盤

## 画面表示

- 出題画面（DrillUiState.Question）: `attemptCount`（この問題の解答回数）と
  `totalCandidates`（全候補数）を「この問題の解答回数: n回　全m問」として表示
- 結果画面: 最善手の読み筋を再生でき、末尾の▶+でエンジン解析による読み筋延長ができる
  （`ui/PvExtensionRunner`）

## 実装場所

- 出題ローテーション: `shared/src/commonMain/.../drill/DrillRotation.kt`
  （テスト: `shared/src/jvmTest/.../drill/DrillRotationTest.kt`）
- 候補取得: `DatabaseRepository.getDrillCandidates()`
- 解答回数集計: `DatabaseRepository.getDrillAttemptCounts()`（SQL: `getDrillAttemptCountAll`）
- 画面: `ui/DrillScreen.kt` ＋ `ui/DrillViewModel.kt`（Android配線: `androidApp/DrillScreenHost.kt`）

## VRT

ドリル画面のゴールデンは `androidApp/src/test/snapshots/drill_*.png`（手順は vrt.md）。
