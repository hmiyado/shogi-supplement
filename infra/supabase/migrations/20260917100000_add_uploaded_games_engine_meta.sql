-- 解析結果の来歴を、ローカルDBとの同期対象にも保持する。
-- 旧クライアントはこの列を送らないため、NULL許容で後方互換にする。
alter table public.uploaded_games
  add column engine_meta jsonb;

alter table public.uploaded_games
  add constraint uploaded_games_engine_meta_size_limit
  check (engine_meta is null or pg_column_size(engine_meta) <= 4096);
