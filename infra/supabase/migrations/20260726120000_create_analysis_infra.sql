-- analysis-worker専用テーブル（クライアント不可視）。
-- Why not RLSポリシーを作る: service_roleはRLSをバイパスするためワーカーからは読み書きでき、
-- anon/authenticatedからは行が一切見えない。アプリはワーカー経由でしかここへ触れないので、
-- 行単位のアクセス制御を組む相手がいない。

create table analysis_jobs (
  id            uuid primary key default gen_random_uuid(),
  user_id       uuid not null references auth.users(id) on delete cascade,
  moves_hash    text not null,                   -- USI手列のSHA-256（冪等キー）
  -- Why not NOT NULL: TTLスイープが7日経過後にNULL化する対象。
  moves_usi     jsonb,
  status        text not null default 'running', -- running|done|error
  result_json   jsonb,                           -- 局面ごとのMultiPV結果（PvInfo互換。TTL対象）
  engine_meta   jsonb,                           -- engine_rev/eval_sha256/解析条件（TTL対象外）
  error         text,
  created_at    timestamptz not null default now(),
  finished_at   timestamptz,
  unique (user_id, moves_hash)
);
alter table analysis_jobs enable row level security;

-- Why not unique(user_id, moves_hash)のインデックスで足りるか: あれはmoves_hash完全一致に
-- しか効かない。クォータ判定は当日分をcreated_atの範囲で数えるため別途必要。
create index analysis_jobs_user_id_created_at_idx
  on analysis_jobs (user_id, created_at);

create table user_bans (
  user_id    uuid primary key references auth.users(id) on delete cascade,
  reason     text,
  created_at timestamptz not null default now()
);
alter table user_bans enable row level security;

create table quota_limits (
  user_id     uuid primary key references auth.users(id) on delete cascade,
  daily_limit int not null default 30
);
alter table quota_limits enable row level security;

-- TTL: finished_atから7日経過したジョブの棋譜本体と解析結果をNULL化する。
-- Why not 行ごと削除する: クォータ集計と計測に使い続けるため。消すのは棋譜だけでよい。
create extension if not exists pg_cron;

-- security definer + search_path固定: スケジュール実行者の権限と検索パスに依存させず、
-- 対象テーブルを常にpublicスキーマで解決させる。
create or replace function analysis_jobs_ttl_sweep()
returns void
language sql
security definer
set search_path = ''
as $$
  update public.analysis_jobs
  set moves_usi = null,
      result_json = null
  where finished_at < now() - interval '7 days'
    and (moves_usi is not null or result_json is not null);
$$;

select cron.schedule(
  'analysis_jobs_ttl_sweep',
  '0 3 * * *',
  $$select public.analysis_jobs_ttl_sweep();$$
);
