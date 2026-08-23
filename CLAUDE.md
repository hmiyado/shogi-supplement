# shogi-supplement

セッション開始時は tmp/HANDOVER.md を読むこと（現在地と次の一手）。
tmp/ は作業ログ置き場（git管理外）。設計資料は docs/ に置いてコミットする。

## Design System

UI・ビジュアルに関する判断の前に必ず DESIGN.md を読むこと。
フォント・色・余白・美学の方向はすべてそこで定義されている。
メンテナの明示的な承認なしに逸脱しない。
QA・レビュー時は DESIGN.md に合わない実装をフラグする。
ユーザー向け文言は docs/wording.md（文言表）に従い、反する文言もフラグする。

## コミット前の自己レビュー

**コミットを作る前に必ず self-code-review を実施する**
（.claude/skills/self-code-review/SKILL.md。/self-code-review でも起動可）。
混入チェック・文言・デザイン準拠の規定はスキルにある。
コメント規約の正は docs/comment-policy.md（機械チェックは tools/comment_lint.py）。
委譲されたエージェントがコミットを作る場合も同様。

## リリース

バージョンを出すときは app/docs/release-checklist.md に従う。
バージョン番号とリリースノートは6ファイルに分散していて、漏らすと不整合になる。
