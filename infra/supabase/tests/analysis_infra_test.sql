-- pgTAP: 20260726120000_create_analysis_infra.sql のSQL関数・トリガー・制約のテスト。
-- 実行: cd infra && supabase test db（ローカルDockerのDBに全マイグレーション適用後に走る。
-- 本番には触れない）。
--
-- now()はモックできないため、時刻依存のケースは created_at / finished_at を
-- 明示指定した行で境界を作る。
begin;
create extension if not exists pgtap with schema extensions;

select plan(17);

-- ── テスト用ユーザー ─────────────────────────────────────────────────────
-- 匿名サインインで作られる行の最小形。トリガー・RLSの主体として使う。
insert into auth.users (id, instance_id, aud, role, created_at, updated_at)
values
  ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', now(), now()),
  ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', now(), now());

-- ── analysis_jobs_ttl_sweep ──────────────────────────────────────────────
insert into public.analysis_jobs (user_id, moves_hash, moves_usi, result_json, finished_at)
values
  ('00000000-0000-0000-0000-000000000001', 'hash-old', '["7g7f"]', '{"pv": []}',
   now() - interval '8 days'),
  ('00000000-0000-0000-0000-000000000001', 'hash-recent', '["2g2f"]', '{"pv": []}',
   now() - interval '6 days'),
  ('00000000-0000-0000-0000-000000000001', 'hash-unfinished', '["6i7h"]', null, null);

select lives_ok(
  $$select public.analysis_jobs_ttl_sweep()$$,
  'ttl_sweep: 実行できる'
);

select is(
  (select moves_usi is null and result_json is null
     from public.analysis_jobs where moves_hash = 'hash-old'),
  true,
  'ttl_sweep: 7日超過のジョブは棋譜と解析結果がNULL化される'
);

select is(
  (select moves_usi is not null
     from public.analysis_jobs where moves_hash = 'hash-recent'),
  true,
  'ttl_sweep: 7日未満のジョブは残る'
);

select is(
  (select moves_usi is not null
     from public.analysis_jobs where moves_hash = 'hash-unfinished'),
  true,
  'ttl_sweep: 未完了（finished_at=null）のジョブは対象外'
);

select is(
  (select count(*)::int from public.analysis_jobs
    where user_id = '00000000-0000-0000-0000-000000000001'),
  3,
  'ttl_sweep: 行そのものは削除されない（クォータ集計用に残る）'
);

-- ── uploaded_games: サイズ上限CHECK ──────────────────────────────────────
select lives_ok(
  $$insert into public.uploaded_games (user_id, content_hash, moves_usi, private_enc)
    values ('00000000-0000-0000-0000-000000000001', 'ch-normal',
            '["7g7f","3c3d"]', repeat('A', 1024))$$,
  'size_limits: 正常サイズの行はinsertできる'
);

select throws_ok(
  $$insert into public.uploaded_games (user_id, content_hash, moves_usi)
    values ('00000000-0000-0000-0000-000000000001', 'ch-big-moves',
            to_jsonb(repeat('x', 60000)))$$,
  '23514', null,
  'size_limits: moves_usiが50KB超の行は弾かれる'
);

select throws_ok(
  $$insert into public.uploaded_games (user_id, content_hash, moves_usi, private_enc)
    values ('00000000-0000-0000-0000-000000000001', 'ch-big-enc',
            '["7g7f"]', repeat('A', 70000))$$,
  '23514', null,
  'size_limits: private_encが64KB超の行は弾かれる'
);

select throws_ok(
  $$insert into public.uploaded_games (user_id, content_hash, moves_usi, headers)
    values ('00000000-0000-0000-0000-000000000001', 'ch-big-headers',
            '["7g7f"]', jsonb_build_object('k', repeat('x', 20000)))$$,
  '23514', null,
  'size_limits: headersが10KB超の行は弾かれる'
);

-- ── uploaded_games: 日次insert上限（50行/日・JST日界）────────────────────
-- user 2 で当日49行（+上のuser 1分は別ユーザーなので数えられないことも同時に確認）
insert into public.uploaded_games (user_id, content_hash, moves_usi)
select '00000000-0000-0000-0000-000000000002', 'ch-bulk-' || n, '["7g7f"]'
from generate_series(1, 49) as n;

select lives_ok(
  $$insert into public.uploaded_games (user_id, content_hash, moves_usi)
    values ('00000000-0000-0000-0000-000000000002', 'ch-50th', '["7g7f"]')$$,
  'daily_limit: 50行目まではinsertできる'
);

select throws_ok(
  $$insert into public.uploaded_games (user_id, content_hash, moves_usi)
    values ('00000000-0000-0000-0000-000000000002', 'ch-51st', '["7g7f"]')$$,
  'P0001', 'daily upload limit reached',
  'daily_limit: 51行目は弾かれる'
);

select throws_ok(
  $$insert into public.uploaded_games (user_id, content_hash, moves_usi)
    values ('00000000-0000-0000-0000-000000000002', 'ch-51st-again', '["7g7f"]')$$,
  'P0001', 'daily upload limit reached',
  'daily_limit: 上限到達後も弾かれ続ける'
);

-- 前日分（JST日界の外）に50行あっても当日のinsertは通る。
-- created_atを「JSTの今日の始まり」より前に付け替えて日界の外へ出す
update public.uploaded_games
set created_at = (date_trunc('day', now() at time zone 'Asia/Tokyo') at time zone 'Asia/Tokyo')
                 - interval '1 hour'
where user_id = '00000000-0000-0000-0000-000000000002';

select lives_ok(
  $$insert into public.uploaded_games (user_id, content_hash, moves_usi)
    values ('00000000-0000-0000-0000-000000000002', 'ch-next-day', '["7g7f"]')$$,
  'daily_limit: 前日分50行はカウントされない（JST日界でリセット）'
);

-- user 1 は当日1行のみなので通る（ユーザー間でカウントが混ざらない）
select lives_ok(
  $$insert into public.uploaded_games (user_id, content_hash, moves_usi)
    values ('00000000-0000-0000-0000-000000000001', 'ch-other-user', '["7g7f"]')$$,
  'daily_limit: 他ユーザーの行数は影響しない'
);

-- ── delete_user ──────────────────────────────────────────────────────────
-- authenticatedロール＋JWTクレームを偽装して本人として呼ぶ（auth.uid()の解決経路ごと検証）
set local role authenticated;
set local request.jwt.claims to '{"sub": "00000000-0000-0000-0000-000000000002", "role": "authenticated"}';

select lives_ok(
  $$select public.delete_user()$$,
  'delete_user: authenticatedロールから実行できる（security definer）'
);

reset role;

select is(
  (select count(*)::int from auth.users
    where id = '00000000-0000-0000-0000-000000000002'),
  0,
  'delete_user: 本人のauth.users行が消える'
);

select is(
  (select count(*)::int from public.uploaded_games
    where user_id = '00000000-0000-0000-0000-000000000002'),
  0,
  'delete_user: uploaded_gamesもcascadeで消える'
);

select * from finish();
rollback;
