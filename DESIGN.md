# Design System — 将棋サプリ「静かな計器盤」

v0.1。アプリのUI・ビジュアルに関する判断はすべて本書を正とする。
逸脱するときはmiyadoさんの明示的な承認を得ること。

## Product Context

- **What this is:** 自分の実戦棋譜を端末内で解析し、レート帯相応の悪手だけを
  「自分専用のドリル」として周回する将棋学習Androidアプリ（KMP/Compose）
- **Who it's for:** lishogi/将棋ウォーズで指す級位者〜有段者。上達したい社会人
- **記憶の核（memorable thing）:** **「根拠で語る学習」**——レート帯のデータと勝率で語る、
  知的で誠実な学習ツール。数字・分析の見せ方が主役
- **競合との差別化:** 将棋アプリは「派手ゲーム系」と「かわいい系」の二極。
  「知的で誠実なデータの見せ方」は空白地帯

## Aesthetic Direction

- **Direction:** 計器盤の和（Utilitarian × 棋書エディトリアル）
- **Decoration level:** minimal寄りのintentional（装飾は和紙質感の背景程度。
  タイポグラフィと数字が語る）
- **Mood:** 静かで正確。将棋盤の前の落ち着きと、観戦記・棋書の活字文化。
  誇張エフェクトの逆——「計測された値」の顔
- **禁止（アンチパターン）:** ゲーミフィケーション記号（炎・バッジ・マスコット・
  レベルアップ演出・紙吹雪）／グラデーションボタン／過剰な角丸／お祝いアニメーション。
  継続の報酬は「悪手率の推移」という数字そのもので表現する

## Typography — 3書体・役割固定

| 役割 | 書体 | 用途 |
|---|---|---|
| Display/見出し | **Shippori Mincho**（600/700） | 画面タイトル・ドリルの問い・レポート見出し。棋書の風格。**見出し専用**（小サイズ本文には使わない） |
| Body/UI | **IBM Plex Sans JP**（400/500/700） | 本文・ボタン・ラベル・説明文 |
| Data | **IBM Plex Mono**（400/500/600、**tabular-nums**） | 勝率・損失・レート・件数・手数・USI表記など**数値と符号は例外なくmono** |

- **Loading:** 3書体ともOFLライセンス→androidApp/res/fontに同梱（GPLv3配布と両立）。
  APKサイズが問題になれば日本語サブセット化を検討
- **Scale（sp）:** display 28–34 / headline 22 / title 17 / body 15 / label·caption 12 /
  data-large 34（mono） / data 13（mono）。行間は本文1.75、数値表示は1.3

## Color — 意味の三色体系

**Approach:** restrained（無彩色ベース＋意味を持つ3色のみ）。
**紺青=良い・進む ／ 朱=痛い・損失 ／ 卵黄=いま注目**。色は装飾ではなく意味。

### Light

| トークン | 値 | 用途 |
|---|---|---|
| bg | `#F7F3EA` | 画面背景（生成り・和紙） |
| surface | `#FFFDF7` | カード |
| surface2 | `#EFE9DC` | 控えめな面 |
| ink | `#211E1A` | 主文字（濃墨） |
| ink2 / ink3 | `#5C564C` / `#8C857B` | 副文字・キャプション（鼠系） |
| line | `#DDD5C4` | 罫線 |
| **primary（紺青）** | `#3A4B7C` | ボタン・リンク・最善手・改善値・合法手ドット |
| primary-soft | `#E4E8F2` | 紺青の面（済みバッジ等） |
| **highlight（卵黄）** | `#EEDD77` | **面専用**: 選択マス・今日の1問の座布団・注目面。**文字色に使用禁止（コントラスト不足）** |
| highlight-soft | `#F7EFC5` | 卵黄の淡い面 |
| **loss（朱）** | `#C73E3A` | **損失専用**: 悪手・勝率損失・不正解・削除。他用途に使わない |
| loss-soft | `#F5E0DE` | 朱の面 |
| board / board-line | `#E9C98E` / `#A98B54` | 盤（淡い榧）・盤線 |

### Dark（墨ベース。彩度を落として持ち上げる）

| トークン | 値 |
|---|---|
| bg / surface / surface2 | `#1A1815` / `#242119` / `#2E2A21` |
| ink / ink2 / ink3 | `#EAE4D6` / `#B5AC9C` / `#857D6E` |
| line | `#3B362B` |
| primary / primary-soft | `#8FA3D4` / `#2A3147` |
| highlight / highlight-soft | `#D9C766` / `#3B371F` |
| loss / loss-soft | `#E06B62` / `#43261F` |
| board / board-line | `#B69A62` / `#8A7040` |

- **Dynamic color（Material You）は使わない**——ブランド色が意味を担うため固定
- Semantic: success=primary系で表現（別の緑は導入しない）／error=loss（朱）／
  info=primary-soft面＋ink

## Spacing

- **Base unit:** 4dp
- **Density:** comfortable（数値テーブル・計器表示のみcompact可）
- **Scale:** 2xs(2) xs(4) sm(8) md(16) lg(24) xl(32) 2xl(48)

## Layout

- **Approach:** grid-disciplined（8dpリズム）。レポート画面のみ棋書風の強い見出し階層
- **Border radius:** sm 4 / md 8 / lg 12（カード12・ボタン8・バッジ4。fullは使わない——
  チップのみ999可）
- **Elevation:** 影は最小限（カードに薄く1段のみ）
- **No-jitter原則:** 状態変化（モード切替・ローディング・条件付き行の
  出現）でレイアウトの高さを変えない。計器類は**固定高さのスロット**とし、状態は
  スロット内容の**排他的な入れ替え**で表現する（行の追加・削除は禁止）。スピナー等の
  インジケータもスロットの行高に収める。検収時はモード切替前後で罫線Y座標が
  一致することをピクセルで確認する

## Motion

- **Approach:** minimal-functional＋盤上だけ丁寧
- **Duration:** 画面遷移・状態変化 150–250ms ／ 駒の移動・正誤の提示 250–350ms
- **Easing:** enter=ease-out / exit=ease-in / move=ease-in-out
- 祝祭・注意引きのアニメーションは使わない

## Component Rules

- **ボタン:** primary=紺青塗り／secondary=紺青アウトライン／ghost=文字のみ／
  destructive=朱アウトライン（塗りは使わない）
- **正誤表示:** 正解=紺青系・簡潔（「正解。」＋mono数値）／不正解=朱系・簡潔。バナーは静かに
- **チップ:** 判定（◎○）=primary-soft／分類カテゴリ=loss-soft（悪手系）または line枠／
  注目=卵黄
- **数値の符号規約:** 改善・良化=紺青、損失・悪化=朱、中立=ink。プラスマイナス記号を必ず付ける
- **盤:** 文字駒は明朝。後手駒は180度回転。選択マス=卵黄、移動先候補=紺青ドット、
  伝統的な見た目（榧色・墨文字）を崩さない

## SAFE / RISK（設計判断の記録）

- SAFE: Material3骨格・標準ナビ維持／習慣導線（今日の1問・短セッション）／盤駒は伝統踏襲
- RISK1: 明朝見出し（見出し限定で可読性リスクを回避）
- RISK2: ゲーミフィケーション記号の全面不使用（報酬は数字の推移で表現）
- RISK3: 数値全mono＋三色主義（華やかさより計器の信頼感）

## プレビュー・適用状況

- プレビューHTML: scratchpadに生成（揮発）。再生成時は本書のトークンから
- **画面モック（ホーム/レポート/ドリルの詳細レイアウト）は未確定**。
  本書が確定しているのはトークンとルールのレベル。画面単位の適用は個別に検収する
