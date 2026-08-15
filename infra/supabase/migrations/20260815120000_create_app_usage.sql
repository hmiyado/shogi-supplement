-- 利用中のアプリ版の記録。引き継ぎコードの書式を変えたときに、古い版のままの利用者が
-- どれだけ残っているかを判断するために使う。
--
-- 主キーを (user_id, platform) にするのは、引き継ぎで同じアカウントが複数の環境へ
-- 広がるため。user_idだけで一意にすると、後から来たリクエストが別環境の記録を消す。
--
-- platformに検査制約を置かないのは、環境が増えるたびにマイグレーションを要求しないため。
-- 語彙は app_policy.platform と同じ（android / ios、Debugビルドは末尾 -dev）。
--
-- buildは単調増加する整数（gradle.properties の versionCode が単一の源）。
-- 版の新旧を比較できる形を保つため、表示バージョンは持たない。
create table if not exists public.app_usage (
  user_id    uuid        not null references auth.users (id) on delete cascade,
  platform   text        not null,
  build      integer     not null,
  updated_at timestamptz not null default now(),
  primary key (user_id, platform)
);

alter table public.app_usage enable row level security;

-- 書き込みはワーカー（service_role）だけが行う。クライアントに自己申告させないのは、
-- サーバーが実際に受け取ったヘッダだけを記録に残すため。
-- 参照は管理画面が接続文字列で直接読むため、ポリシーは置かない。
revoke all on public.app_usage from anon, authenticated;
grant select, insert, update on public.app_usage to service_role;
