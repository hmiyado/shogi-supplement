# 棋譜収集（アカウント＋アップロード）設計

データ提供（任意機能）の設計。

## 方針

- **ローカルファースト**: デフォルトは全データ端末内。アカウントなしでアプリの全機能
  （解析・レポート・ドリル）が使える。この性質は変えない
- **アカウントは任意**: サーバー管理したいユーザーだけアカウント作成＋棋譜アップロード。
  ユーザー価値=バックアップ（機種変更時の復元）、運営価値=将来の学習データ
  （利用規約に研究利用を明記して同意を取る）
- **匿名認証**: Supabase Anonymous sign-in を使用。メールアドレス・パスワードは扱わない。
  生のuid（UUID）はUI・ログ・Sentryに一切露出しない
- **機種変更**: 提供済みデータの紐付けは引き継がれない（画面と規約に明記）
- インフラ=**Supabase**（Postgres直で学習用エクスポートが容易）

## アーキテクチャ

- クライアント: **supabase-kt**（supabase-community、KMP対応。Auth＋Postgrestモジュール）を
  `:shared` に導入。ネットワーク層はinterface注入にしてJVMテストはfakeで完結させる
- 埋め込むのは URL＋anon key のみ（RLS前提で埋め込み可。service_roleは絶対に埋め込まない）

## スキーマ（Supabase側）

```sql
create table uploaded_games (
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid not null references auth.users(id) on delete cascade,
  content_hash text not null,          -- 端末側game.content_hashと同一
  moves_usi    jsonb not null,         -- USI手列（学習用の主データ。端末DBに常にある）
  kif_text     text,                   -- 棋譜原文（任意）
  rating       int,                    -- 解析時点の推定棋力（悪手率ベース。申告値ではない）
  rating_sample_moves int,             -- 上記推定に使った集計対象手数（今局＋過去累計）
  move_count   int,
  coef_version text,                   -- 解析条件の記録（非機能要件）
  analysis_json jsonb,                 -- 悪手レポート一式（再計算可能だが送っておく）
  created_at   timestamptz not null default now(),
  unique (user_id, content_hash)       -- 同一棋譜の重複アップロード防止
);
alter table uploaded_games enable row level security;
create policy "own rows" on uploaded_games
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
```

- 研究用エクスポートは service_role で運営側から行う（クライアント権限では他人の行は見えない）
- 退会: アカウント削除→cascadeで棋譜も削除（プライバシーポリシー要件）

### アカウント削除RPC

```sql
create or replace function delete_user() returns void
language sql security definer set search_path = ''
as $$
  delete from auth.users where id = auth.uid();
$$;

revoke execute on function public.delete_user() from public, anon;
grant execute on function public.delete_user() to authenticated;
```

- `security definer` で auth.users への delete 権限を関数側に持たせ、`auth.uid()` で
  「自分自身のみ」削除可能にする
- 関数はデフォルトで PUBLIC に実行許可があるため、`anon`/`public` からの実行を明示的に
  `revoke` し、`authenticated` のみに `grant` する多層防御を行う
- uploaded_games は `references auth.users(id) on delete cascade` のため同時に全行削除される

## アプリ側フロー

1. ホームに「アカウント」導線（設定的な控えめ配置）
2. アカウント画面で「データ提供を有効にする」を選ぶと匿名サインインが実行される。
   同意日時を `user_settings.consent_accepted_at` に記録
3. ログイン中: 解析済み棋譜一覧に「アップロード」状態を表示。
   一括アップロード＋「解析後に自動アップロード」トグル（デフォルトOFF）
4. ログアウト/未ログインでも全機能はローカルで動く（アップロード導線が消えるだけ）
5. アカウント削除時は端末DBの `uploaded_at` を全リセットし、再アップロード可能な状態に戻す。
   端末内の棋譜・解析・ドリルはそのまま残る（ローカルファースト）

## 規約のホスティング

- 利用規約・プライバシーポリシーはGitHub Pagesでホストし（`docs/terms.html`）、
  アプリはURLリンクのみを持つ（同梱コピーは持たない）。同意フロー（アカウント作成）は
  ネットワーク前提なのでオフライン同梱は不要
- 同意記録は `consent_accepted_at` で担保する
- **OSSライセンス画面（AboutLibraries）は同梱**（GPLv3の表示義務は配布物側に必要なため）

## 未決・将来

- 複数端末同期（ダウンロード方向）はスコープ外。アップロードのみ
- 匿名化ポリシー: 対局者名はkif_text内に残る。研究エクスポート時にマスクする方針
  （クライアント側で除去する選択肢もあり）
