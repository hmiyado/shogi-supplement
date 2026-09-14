-- 申告内容を、棋譜のアップロード時点で再現できるよう追記する。
create table public.rating_declarations (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  rating_service text,
  rating_raw integer,
  rating_rule text not null default '',
  declared_at timestamptz not null,
  unique (user_id, declared_at, rating_service, rating_rule)
);

alter table public.rating_declarations enable row level security;

create policy "own rows"
  on public.rating_declarations for all
  to authenticated
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

create index rating_declarations_user_declared_idx
  on public.rating_declarations (user_id, declared_at desc);
