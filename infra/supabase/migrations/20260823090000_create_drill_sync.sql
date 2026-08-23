-- 次の一手問題と解答履歴を所有者単位で同期するためのテーブルを追加する。

-- 次の一手問題の出題対象となる所有者限定データ。将来共有する場合はRLSを緩めず、
-- 同意済みの問題を匿名化した別テーブルへコピーする。
create table public.drill_problems (
  id             uuid primary key default gen_random_uuid(),
  -- 元の対局を指した本人。所有者限定データであり、共有時はこの列を公開しない
  user_id        uuid not null references auth.users(id) on delete cascade,
  content_hash   text not null,
  ply            integer not null,
  side           text not null,
  sfen_before    text not null,
  move_usi       text not null,
  best_usi       text,
  loss_wp        double precision not null,
  category       text not null,
  verdict        text not null,
  note           text not null,
  problem_type   text not null,
  priority       double precision not null,
  second_usi     text,
  second_cp      integer,
  created_at     timestamptz not null default now(),
  unique (user_id, content_hash, ply),
  foreign key (user_id, content_hash)
    references public.uploaded_games (user_id, content_hash) on delete cascade,
  constraint drill_problems_input_limits check (
    content_hash ~ '^[0-9a-f]{64}$'
    and ply between 0 and 10000
    and side in ('sente', 'gote')
    and octet_length(sfen_before) <= 1024
    and octet_length(move_usi) <= 16
    and (best_usi is null or octet_length(best_usi) <= 16)
    and octet_length(category) <= 64
    and octet_length(verdict) <= 32
    and octet_length(note) <= 4096
    and octet_length(problem_type) <= 64
    and (second_usi is null or octet_length(second_usi) <= 16)
  )
);
alter table public.drill_problems enable row level security;

create policy "own rows"
  on public.drill_problems for all
  to authenticated
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

create index drill_problems_user_created_idx
  on public.drill_problems (user_id, created_at);

-- 1ユーザーあたりの日次insert上限（JST日界・500行/日）。uploaded_games の 50行/日 と同じ形。
-- Why not advisory lock・カウンタ表: 並列insertでは上限を超え得るが、これは悪用防止の
-- 安全弁であって厳密に守る基準ではない。DBに2種類の上限機構を並べない方を採る。
-- Why not 素の件数チェックだけ: 既存行の再insertを数に入れると、問題再同期
-- （毎回全件を送り直す）が上限到達後にエラーで止まる。重複は数える前に通す。
create or replace function public.drill_problems_daily_limit()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if exists (
    select 1 from public.drill_problems
    where user_id = new.user_id
      and content_hash = new.content_hash
      and ply = new.ply
  ) then
    return new;
  end if;

  if (
    select count(*)
    from public.drill_problems
    where user_id = new.user_id
      and created_at >= (date_trunc('day', now() at time zone 'Asia/Tokyo') at time zone 'Asia/Tokyo')
  ) >= 500 then
    raise exception 'daily drill problem limit reached';
  end if;
  return new;
end;
$$;

create trigger drill_problems_daily_limit
  before insert on public.drill_problems
  for each row execute function public.drill_problems_daily_limit();

create table public.drill_attempts (
  id             uuid primary key default gen_random_uuid(),
  user_id        uuid not null references auth.users(id) on delete cascade,  -- 解答者
  -- Why not (problem_id, user_id) の複合外部キー: 解答者と問題所有者の一致を強制すると、
  -- 将来「他ユーザーの悪手を解く」共有出題で解答を記録できなくなる。他人のproblem_idは
  -- drill_problems のRLSで参照できずuuidの推測も現実的でないため、整合はアプリ側で担保する
  problem_id     uuid not null references public.drill_problems(id) on delete cascade,
  client_attempt_id uuid not null,
  user_move_usi  text not null,
  is_correct     boolean not null,
  loss_wp        double precision,
  attempted_at   timestamptz not null,
  created_at     timestamptz not null default now(),
  unique (user_id, client_attempt_id),
  constraint drill_attempts_input_limits check (
    octet_length(user_move_usi) <= 16
    and attempted_at between timestamptz '2020-01-01' and now() + interval '1 day'
  )
);
alter table public.drill_attempts enable row level security;

create policy "own rows"
  on public.drill_attempts for all
  to authenticated
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

create index drill_attempts_user_created_idx
  on public.drill_attempts (user_id, created_at);

-- 上限の方針は drill_problems と同じ。重複再送は数える前に通す。
create or replace function public.drill_attempts_daily_limit()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if exists (
    select 1 from public.drill_attempts
    where user_id = new.user_id
      and client_attempt_id = new.client_attempt_id
  ) then
    return new;
  end if;

  if (
    select count(*)
    from public.drill_attempts
    where user_id = new.user_id
      and created_at >= (date_trunc('day', now() at time zone 'Asia/Tokyo') at time zone 'Asia/Tokyo')
  ) >= 500 then
    raise exception 'daily drill attempt limit reached';
  end if;
  return new;
end;
$$;

create trigger drill_attempts_daily_limit
  before insert on public.drill_attempts
  for each row execute function public.drill_attempts_daily_limit();

revoke all on public.drill_problems, public.drill_attempts
  from anon, authenticated, service_role;
grant select, insert on public.drill_problems to authenticated;
grant select, insert on public.drill_attempts to authenticated;

revoke execute on function public.drill_problems_daily_limit()
  from public, anon, authenticated, service_role;
revoke execute on function public.drill_attempts_daily_limit()
  from public, anon, authenticated, service_role;
