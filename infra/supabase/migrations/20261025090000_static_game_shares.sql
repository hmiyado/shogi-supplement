create table public.game_share_objects (
  id text primary key check (id ~ '^[A-Za-z0-9_-]{22,64}$'),
  owner_id uuid not null references auth.users(id) on delete cascade,
  content_hash text not null,
  source_table text not null check (source_table in ('uploaded_games','uploaded_games_v2')),
  published boolean not null default false
);
create table public.game_share_deletions (
  object_id text primary key,
  next_attempt_at timestamptz not null default now() + interval '2 minutes',
  expires_at timestamptz not null default now() + interval '24 hours'
);
create index game_share_objects_source on public.game_share_objects(owner_id,content_hash,source_table);
create index game_share_deletions_due on public.game_share_deletions(next_attempt_at);
alter table public.game_share_objects enable row level security;
alter table public.game_share_deletions enable row level security;
revoke all on public.game_share_objects, public.game_share_deletions from public, anon, authenticated;
grant select, insert, update, delete on public.game_share_objects, public.game_share_deletions to service_role;

create function public.reserve_game_share(p_id text,p_owner uuid,p_hash text) returns boolean
language plpgsql security definer set search_path = '' as $$
declare source_name text;
begin
  perform pg_advisory_xact_lock(hashtextextended(p_owner::text || ':' || p_hash, 0));
  if exists(select 1 from public.deleted_analysis_generations where user_id=p_owner and content_hash=p_hash) then
    return false;
  end if;
  perform 1 from public.uploaded_games_v2 where user_id=p_owner and content_hash=p_hash for key share;
  if found then source_name:='uploaded_games_v2'; else
    perform 1 from public.uploaded_games where user_id=p_owner and content_hash=p_hash for key share;
    if not found then return false; end if;
    source_name:='uploaded_games';
  end if;
  insert into public.game_share_objects(id,owner_id,content_hash,source_table)
  values(p_id,p_owner,p_hash,source_name);
  return true;
end $$;

create function public.enqueue_game_share_deletion() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  insert into public.game_share_deletions(object_id) values(old.id) on conflict do nothing;
  return old;
end $$;
create trigger enqueue_game_share_deletion after delete on public.game_share_objects
for each row execute function public.enqueue_game_share_deletion();

create function public.delete_source_game_shares() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  delete from public.game_share_objects
  where owner_id=old.user_id and content_hash=old.content_hash and source_table=tg_table_name;
  return old;
end $$;
create trigger delete_source_game_shares after delete on public.uploaded_games
for each row execute function public.delete_source_game_shares();
create trigger delete_source_game_shares after delete on public.uploaded_games_v2
for each row execute function public.delete_source_game_shares();

create function public.delete_logically_deleted_game_shares() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  delete from public.game_share_objects where owner_id=new.user_id and content_hash=new.content_hash;
  return new;
end $$;
create trigger delete_logically_deleted_game_shares after insert on public.deleted_analysis_generations
for each row execute function public.delete_logically_deleted_game_shares();

create function public.finish_game_share(p_id text,p_owner uuid) returns boolean
language plpgsql security definer set search_path = '' as $$
begin
  update public.game_share_objects set published=true where id=p_id and owner_id=p_owner;
  return found;
end $$;

-- 未完了の発行も収集する。削除記録は残し、タイムアウト後に届いた遅延アップロードも再削除する。
alter table public.game_share_objects add column created_at timestamptz not null default now();
create index game_share_objects_unfinished on public.game_share_objects(created_at) where not published;
create or replace function public.collect_unfinished_game_shares() returns void
language plpgsql security definer set search_path = '' as $$
begin
  delete from public.game_share_objects where not published and id in (
    select id from public.game_share_objects
    where not published and created_at < now()-interval '10 minutes'
    order by created_at limit 100
  );
end $$;

revoke all on function public.reserve_game_share(text,uuid,text), public.finish_game_share(text,uuid),
 public.collect_unfinished_game_shares(), public.enqueue_game_share_deletion(), public.delete_source_game_shares(),
 public.delete_logically_deleted_game_shares()
 from public,anon,authenticated;
grant execute on function public.reserve_game_share(text,uuid,text), public.finish_game_share(text,uuid),
 public.collect_unfinished_game_shares() to service_role;

insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values('game-shares','game-shares',true,65536,array['application/json'])
on conflict(id) do nothing;
-- 公開取得はStorageのpublic経路だけ。storage.objectsへのSELECTポリシーは追加しない。
