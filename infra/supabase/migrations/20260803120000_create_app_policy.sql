-- 強制アップデートのポリシー保存先。GH Pages静的JSON案は却下し、
-- Supabaseテーブルにした（管理画面PUTの即時反映を優先。緊急停止用途では
-- 静的JSONの「gitコミット→GH Pagesのキャッシュ反映待ち」のラグが許容できない）。
-- クライアント（未実装・別タスク）は起動時にanonでSELECTするだけの読み取り専用。

create table public.app_policy (
  -- 'common'行はmessageのみを持つ全プラットフォーム共通行（min_build等はNULL）。
  -- Why not別テーブル: プラットフォーム別の2行と共通の1行で計3行に収まる規模のため、
  -- テーブルを分けるより単一テーブルの行種別で十分
  platform          text primary key check (platform in ('android', 'ios', 'common')),
  min_build         integer,
  store_url         text,
  message           text,
  updated_at        timestamptz not null default now(),
  constraint app_policy_build_required check (
    platform = 'common' or min_build is not null
  )
);
alter table public.app_policy enable row level security;

create policy "app_policy_select_all"
  on public.app_policy for select
  to anon, authenticated
  using (true);

revoke all on public.app_policy from anon, authenticated, service_role;
grant select on public.app_policy to anon, authenticated;
grant select, insert, update on public.app_policy to service_role;

create or replace function public.app_policy_set_updated_at()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

create trigger app_policy_set_updated_at
  before update on public.app_policy
  for each row execute function public.app_policy_set_updated_at();

revoke execute on function public.app_policy_set_updated_at()
  from public, anon, authenticated, service_role;

-- 初期値: minBuild=1（誰もブロックしない）。ストアURL・messageは未定のため空。
insert into public.app_policy (platform, min_build, store_url, message)
values
  ('android', 1, '', null),
  ('ios', 1, '', null),
  ('common', null, null, '')
on conflict (platform) do nothing;

-- ── 変更監査（insert-only）─────────────────────────────────────────────
-- app_policyへのinsert/updateをトリガーで自動記録する。Why not書き込み側が都度記録する方式:
-- 書き込み経路が管理画面以外に増えても（手動SQL等）漏れなく残る。
create table public.app_policy_history (
  id                uuid primary key default gen_random_uuid(),
  platform          text not null,
  min_build         integer,
  store_url         text,
  message           text,
  changed_at        timestamptz not null default now()
);
alter table public.app_policy_history enable row level security;

revoke all on public.app_policy_history from anon, authenticated, service_role;
grant select, insert on public.app_policy_history to service_role;

create or replace function public.app_policy_record_history()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.app_policy_history (platform, min_build, store_url, message)
  values (new.platform, new.min_build, new.store_url, new.message);
  return new;
end;
$$;

create trigger app_policy_history_trigger
  after insert or update on public.app_policy
  for each row execute function public.app_policy_record_history();

revoke execute on function public.app_policy_record_history()
  from public, anon, authenticated, service_role;
