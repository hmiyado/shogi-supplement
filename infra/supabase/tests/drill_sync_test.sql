-- pgTAP: 20260823090000_create_drill_sync.sql のRLS・制約・トリガーのテスト。
-- 実行: cd infra && supabase test db（ローカルDockerのDBに全マイグレーション適用後に走る。
-- 本番には触れない）。
begin;
create extension if not exists pgtap with schema extensions;

select plan(37);

-- ── テスト用ユーザーとuploaded_games ────────────────────────────────────
insert into auth.users (id, instance_id, aud, role, created_at, updated_at)
values
  ('20000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', now(), now()),
  ('20000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', now(), now()),
  ('20000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', now(), now()),
  ('20000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', now(), now()),
  ('20000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', now(), now()),
  ('20000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', now(), now()),
  ('20000000-0000-0000-0000-000000000007', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', now(), now()),
  ('20000000-0000-0000-0000-000000000008', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', now(), now()),
  ('20000000-0000-0000-0000-000000000009', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', now(), now()),
  ('20000000-0000-0000-0000-000000000010', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', now(), now());

insert into public.uploaded_games (user_id, content_hash, moves_usi)
values
  ('20000000-0000-0000-0000-000000000001', repeat('1', 64), '["7g7f"]'),
  ('20000000-0000-0000-0000-000000000002', repeat('2', 64), '["7g7f"]'),
  ('20000000-0000-0000-0000-000000000003', repeat('3', 64), '["7g7f"]'),
  ('20000000-0000-0000-0000-000000000004', repeat('4', 64), '["7g7f"]'),
  ('20000000-0000-0000-0000-000000000005', repeat('5', 64), '["7g7f"]'),
  ('20000000-0000-0000-0000-000000000006', repeat('6', 64), '["7g7f"]'),
  ('20000000-0000-0000-0000-000000000007', repeat('7', 64), '["7g7f"]'),
  ('20000000-0000-0000-0000-000000000008', repeat('8', 64), '["7g7f"]'),
  ('20000000-0000-0000-0000-000000000009', repeat('9', 64), '["7g7f"]'),
  ('20000000-0000-0000-0000-000000000010', repeat('a', 64), '["7g7f"]');

-- ── RLS「own rows」───────────────────────────────────────────────────────
insert into public.drill_problems (
  id, user_id, content_hash, ply, side, sfen_before, move_usi,
  loss_wp, category, verdict, note, problem_type, priority
)
values (
  '30000000-0000-0000-0000-000000000002',
  '20000000-0000-0000-0000-000000000002', repeat('2', 64), 1, 'sente',
  'startpos', '7g7f', 0.0, 'tactical', 'correct', 'seed', 'next_move', 0.0
);

set local role authenticated;
set local request.jwt.claims to '{"sub": "20000000-0000-0000-0000-000000000001", "role": "authenticated"}';

select lives_ok(
  $$insert into public.drill_problems (
      id, user_id, content_hash, ply, side, sfen_before, move_usi,
      loss_wp, category, verdict, note, problem_type, priority
    ) values (
      '30000000-0000-0000-0000-000000000001',
      '20000000-0000-0000-0000-000000000001', repeat('1', 64), 1, 'sente',
      'startpos', '7g7f', 0.0, 'tactical', 'correct', 'own', 'next_move', 0.0
    )$$,
  'RLS: drill_problemsは本人の行をinsertできる'
);

select lives_ok(
  $$insert into public.drill_attempts (
      id, user_id, problem_id, client_attempt_id, user_move_usi,
      is_correct, attempted_at
    ) values (
      '40000000-0000-0000-0000-000000000001',
      '20000000-0000-0000-0000-000000000001',
      '30000000-0000-0000-0000-000000000001',
      '50000000-0000-0000-0000-000000000001', '7g7f', true, now()
    )$$,
  'RLS: drill_attemptsは本人の行をinsertできる'
);

select throws_ok(
  $$insert into public.drill_problems (
      user_id, content_hash, ply, side, sfen_before, move_usi,
      loss_wp, category, verdict, note, problem_type, priority
    ) values (
      '20000000-0000-0000-0000-000000000002', repeat('2', 64), 2, 'sente',
      'startpos', '7g7f', 0.0, 'tactical', 'correct', 'forged', 'next_move', 0.0
    )$$,
  '42501', null,
  'RLS: drill_problemsは他人のuser_idを騙ったinsertを拒否する'
);

select throws_ok(
  $$insert into public.drill_attempts (
      user_id, problem_id, client_attempt_id, user_move_usi,
      is_correct, attempted_at
    ) values (
      '20000000-0000-0000-0000-000000000002',
      '30000000-0000-0000-0000-000000000002',
      '50000000-0000-0000-0000-000000000002', '7g7f', true, now()
    )$$,
  '42501', null,
  'RLS: drill_attemptsは他人のuser_idを騙ったinsertを拒否する'
);

select is(
  (select count(*)::int from public.drill_problems),
  1,
  'RLS: drill_problemsは他人の行をselectで見せない'
);

select is(
  (select count(*)::int from public.drill_attempts),
  1,
  'RLS: drill_attemptsは他人の行をselectで見せない'
);

set local role anon;
set local request.jwt.claims to '{"role": "anon"}';

select throws_ok(
  $$select count(*) from public.drill_problems$$,
  '42501', null,
  'RLS: anonはdrill_problemsをselectできない'
);

select throws_ok(
  $$select count(*) from public.drill_attempts$$,
  '42501', null,
  'RLS: anonはdrill_attemptsをselectできない'
);

reset role;

-- ── uploaded_gamesへの複合外部キー ──────────────────────────────────────
select throws_ok(
  $$insert into public.drill_problems (
      user_id, content_hash, ply, side, sfen_before, move_usi,
      loss_wp, category, verdict, note, problem_type, priority
    ) values (
      '20000000-0000-0000-0000-000000000003', repeat('f', 64), 1, 'sente',
      'startpos', '7g7f', 0.0, 'tactical', 'correct', 'missing', 'next_move', 0.0
    )$$,
  '23503', null,
  'FK: 対応するuploaded_gamesが無いdrill_problemsはinsertできない'
);

select lives_ok(
  $$insert into public.drill_problems (
      id, user_id, content_hash, ply, side, sfen_before, move_usi,
      loss_wp, category, verdict, note, problem_type, priority
    ) values (
      '30000000-0000-0000-0000-000000000003',
      '20000000-0000-0000-0000-000000000003', repeat('3', 64), 1, 'sente',
      'startpos', '7g7f', 0.0, 'tactical', 'correct', 'fk', 'next_move', 0.0
    )$$,
  'FK: 対応するuploaded_gamesがあればdrill_problemsをinsertできる'
);

-- ── uploaded_games削除によるカスケード ──────────────────────────────────
insert into public.drill_problems (
  id, user_id, content_hash, ply, side, sfen_before, move_usi,
  loss_wp, category, verdict, note, problem_type, priority
)
values (
  '30000000-0000-0000-0000-000000000004',
  '20000000-0000-0000-0000-000000000004', repeat('4', 64), 1, 'sente',
  'startpos', '7g7f', 0.0, 'tactical', 'correct', 'cascade', 'next_move', 0.0
);
insert into public.drill_attempts (
  id, user_id, problem_id, client_attempt_id, user_move_usi, is_correct, attempted_at
)
values (
  '40000000-0000-0000-0000-000000000004',
  '20000000-0000-0000-0000-000000000004',
  '30000000-0000-0000-0000-000000000004',
  '50000000-0000-0000-0000-000000000004', '7g7f', true, now()
);

delete from public.uploaded_games
where user_id = '20000000-0000-0000-0000-000000000004'
  and content_hash = repeat('4', 64);

select is(
  (select count(*)::int from public.drill_problems
    where id = '30000000-0000-0000-0000-000000000004'),
  0,
  'cascade: uploaded_gamesの削除でdrill_problemsも消える'
);

select is(
  (select count(*)::int from public.drill_attempts
    where id = '40000000-0000-0000-0000-000000000004'),
  0,
  'cascade: drill_problemsの削除でdrill_attemptsも消える'
);

-- ── drill_problemsの一意制約とupsert ─────────────────────────────────────
insert into public.drill_problems (
  id, user_id, content_hash, ply, side, sfen_before, move_usi,
  loss_wp, category, verdict, note, problem_type, priority
)
values (
  '30000000-0000-0000-0000-000000000005',
  '20000000-0000-0000-0000-000000000005', repeat('5', 64), 1, 'sente',
  'startpos', '7g7f', 0.0, 'tactical', 'correct', 'unique', 'next_move', 0.0
);

select throws_ok(
  $$insert into public.drill_problems (
      user_id, content_hash, ply, side, sfen_before, move_usi,
      loss_wp, category, verdict, note, problem_type, priority
    ) values (
      '20000000-0000-0000-0000-000000000005', repeat('5', 64), 1, 'sente',
      'startpos', '2g2f', 0.1, 'tactical', 'wrong', 'duplicate', 'next_move', 0.1
    )$$,
  '23505', null,
  'unique: drill_problemsの同じ(user_id, content_hash, ply)は重複insertできない'
);

select lives_ok(
  $$insert into public.drill_problems (
      user_id, content_hash, ply, side, sfen_before, move_usi,
      loss_wp, category, verdict, note, problem_type, priority
    ) values (
      '20000000-0000-0000-0000-000000000005', repeat('5', 64), 1, 'sente',
      'startpos', '2g2f', 0.1, 'tactical', 'wrong', 'duplicate', 'next_move', 0.1
    ) on conflict (user_id, content_hash, ply) do nothing$$,
  'upsert: drill_problemsの重複をdo nothingで再送できる'
);

select is(
  (select count(*)::int from public.drill_problems
    where user_id = '20000000-0000-0000-0000-000000000005'
      and content_hash = repeat('5', 64) and ply = 1),
  1,
  'upsert: drill_problemsは重複再送後も1行のまま'
);

-- ── drill_attemptsの一意制約とupsert ────────────────────────────────────
insert into public.drill_problems (
  id, user_id, content_hash, ply, side, sfen_before, move_usi,
  loss_wp, category, verdict, note, problem_type, priority
)
values (
  '30000000-0000-0000-0000-000000000006',
  '20000000-0000-0000-0000-000000000006', repeat('6', 64), 1, 'sente',
  'startpos', '7g7f', 0.0, 'tactical', 'correct', 'attempt-unique', 'next_move', 0.0
);
insert into public.drill_attempts (
  id, user_id, problem_id, client_attempt_id, user_move_usi, is_correct, attempted_at
)
values (
  '40000000-0000-0000-0000-000000000006',
  '20000000-0000-0000-0000-000000000006',
  '30000000-0000-0000-0000-000000000006',
  '50000000-0000-0000-0000-000000000006', '7g7f', true, now()
);

select throws_ok(
  $$insert into public.drill_attempts (
      user_id, problem_id, client_attempt_id, user_move_usi,
      is_correct, attempted_at
    ) values (
      '20000000-0000-0000-0000-000000000006',
      '30000000-0000-0000-0000-000000000006',
      '50000000-0000-0000-0000-000000000006', '2g2f', false, now()
    )$$,
  '23505', null,
  'unique: drill_attemptsの同じ(user_id, client_attempt_id)は重複insertできない'
);

select lives_ok(
  $$insert into public.drill_attempts (
      user_id, problem_id, client_attempt_id, user_move_usi,
      is_correct, attempted_at
    ) values (
      '20000000-0000-0000-0000-000000000006',
      '30000000-0000-0000-0000-000000000006',
      '50000000-0000-0000-0000-000000000006', '2g2f', false, now()
    ) on conflict (user_id, client_attempt_id) do nothing$$,
  'upsert: drill_attemptsの重複をdo nothingで再送できる'
);

select is(
  (select count(*)::int from public.drill_attempts
    where user_id = '20000000-0000-0000-0000-000000000006'
      and client_attempt_id = '50000000-0000-0000-0000-000000000006'),
  1,
  'upsert: drill_attemptsは重複再送後も1行のまま'
);

-- ── drill_problems_input_limits CHECK ───────────────────────────────────
select throws_ok(
  $$insert into public.drill_problems (
      user_id, content_hash, ply, side, sfen_before, move_usi,
      loss_wp, category, verdict, note, problem_type, priority
    ) values (
      '20000000-0000-0000-0000-000000000007', 'too-short', 1, 'sente',
      'startpos', '7g7f', 0.0, 'tactical', 'correct', 'bad hash', 'next_move', 0.0
    )$$,
  '23514', null,
  'input_limits: content_hashが64桁hexでない行は弾かれる'
);

select throws_ok(
  $$insert into public.drill_problems (
      user_id, content_hash, ply, side, sfen_before, move_usi,
      loss_wp, category, verdict, note, problem_type, priority
    ) values (
      '20000000-0000-0000-0000-000000000007', repeat('7', 64), 10001, 'sente',
      'startpos', '7g7f', 0.0, 'tactical', 'correct', 'bad ply', 'next_move', 0.0
    )$$,
  '23514', null,
  'input_limits: plyが10000を超える行は弾かれる'
);

select throws_ok(
  $$insert into public.drill_problems (
      user_id, content_hash, ply, side, sfen_before, move_usi,
      loss_wp, category, verdict, note, problem_type, priority
    ) values (
      '20000000-0000-0000-0000-000000000007', repeat('7', 64), 2, 'invalid',
      'startpos', '7g7f', 0.0, 'tactical', 'correct', 'bad side', 'next_move', 0.0
    )$$,
  '23514', null,
  'input_limits: sideがsente/gote以外の行は弾かれる'
);

select throws_ok(
  $$insert into public.drill_problems (
      user_id, content_hash, ply, side, sfen_before, move_usi,
      loss_wp, category, verdict, note, problem_type, priority
    ) values (
      '20000000-0000-0000-0000-000000000007', repeat('7', 64), 3, 'sente',
      repeat('x', 1025), '7g7f', 0.0, 'tactical', 'correct', 'big sfen', 'next_move', 0.0
    )$$,
  '23514', null,
  'input_limits: sfen_beforeが1024バイトを超える行は弾かれる'
);

select throws_ok(
  $$insert into public.drill_problems (
      user_id, content_hash, ply, side, sfen_before, move_usi,
      loss_wp, category, verdict, note, problem_type, priority
    ) values (
      '20000000-0000-0000-0000-000000000007', repeat('7', 64), 4, 'sente',
      'startpos', '7g7f', 0.0, 'tactical', 'correct', repeat('x', 4097), 'next_move', 0.0
    )$$,
  '23514', null,
  'input_limits: noteが4096バイトを超える行は弾かれる'
);

select lives_ok(
  $$insert into public.drill_problems (
      id, user_id, content_hash, ply, side, sfen_before, move_usi,
      best_usi, loss_wp, category, verdict, note, problem_type,
      priority, second_usi, second_cp
    ) values (
      '30000000-0000-0000-0000-000000000007',
      '20000000-0000-0000-0000-000000000007', repeat('7', 64), 5, 'gote',
      'startpos', '7g7f', '3c3d', 0.5, 'tactical', 'incorrect', 'normal',
      'next_move', 0.5, '2g2f', 20
    )$$,
  'input_limits: drill_problemsの全入力フィールドが上限内ならinsertできる'
);

-- ── drill_attempts_input_limits CHECK ───────────────────────────────────
insert into public.drill_problems (
  id, user_id, content_hash, ply, side, sfen_before, move_usi,
  loss_wp, category, verdict, note, problem_type, priority
)
values (
  '30000000-0000-0000-0000-000000000008',
  '20000000-0000-0000-0000-000000000008', repeat('8', 64), 1, 'sente',
  'startpos', '7g7f', 0.0, 'tactical', 'correct', 'attempt limits', 'next_move', 0.0
);

select throws_ok(
  $$insert into public.drill_attempts (
      user_id, problem_id, client_attempt_id, user_move_usi,
      is_correct, attempted_at
    ) values (
      '20000000-0000-0000-0000-000000000008',
      '30000000-0000-0000-0000-000000000008',
      '50000000-0000-0000-0000-000000000008', repeat('x', 17), true, now()
    )$$,
  '23514', null,
  'attempt_limits: user_move_usiが16バイトを超える行は弾かれる'
);

select throws_ok(
  $$insert into public.drill_attempts (
      user_id, problem_id, client_attempt_id, user_move_usi,
      is_correct, attempted_at
    ) values (
      '20000000-0000-0000-0000-000000000008',
      '30000000-0000-0000-0000-000000000008',
      '50000000-0000-0000-0000-000000000009', '7g7f', true,
      timestamptz '2019-12-31 23:59:59+00'
    )$$,
  '23514', null,
  'attempt_limits: attempted_atが2020-01-01より前の行は弾かれる'
);

select throws_ok(
  $$insert into public.drill_attempts (
      user_id, problem_id, client_attempt_id, user_move_usi,
      is_correct, attempted_at
    ) values (
      '20000000-0000-0000-0000-000000000008',
      '30000000-0000-0000-0000-000000000008',
      '50000000-0000-0000-0000-000000000010', '7g7f', true,
      now() + interval '2 days'
    )$$,
  '23514', null,
  'attempt_limits: attempted_atがnow()+1日より後の行は弾かれる'
);

-- ── 日次上限（drill_problems: 500行/日・JST日界）───────────────────────
insert into public.drill_problems (
  user_id, content_hash, ply, side, sfen_before, move_usi,
  loss_wp, category, verdict, note, problem_type, priority
)
select
  '20000000-0000-0000-0000-000000000009', repeat('9', 64), n, 'sente',
  'startpos', '7g7f', 0.0, 'daily', 'correct', 'bulk', 'next_move', 0.0
from generate_series(1, 499) as series(n);

select lives_ok(
  $$insert into public.drill_problems (
      user_id, content_hash, ply, side, sfen_before, move_usi,
      loss_wp, category, verdict, note, problem_type, priority
    ) values (
      '20000000-0000-0000-0000-000000000009', repeat('9', 64), 500, 'sente',
      'startpos', '7g7f', 0.0, 'daily', 'correct', '500th', 'next_move', 0.0
    )$$,
  'daily_limit: drill_problemsは500行目までinsertできる'
);

select throws_ok(
  $$insert into public.drill_problems (
      user_id, content_hash, ply, side, sfen_before, move_usi,
      loss_wp, category, verdict, note, problem_type, priority
    ) values (
      '20000000-0000-0000-0000-000000000009', repeat('9', 64), 501, 'sente',
      'startpos', '7g7f', 0.0, 'daily', 'correct', '501st', 'next_move', 0.0
    )$$,
  'P0001', 'daily drill problem limit reached',
  'daily_limit: drill_problemsは501行目で弾かれる'
);

select lives_ok(
  $$insert into public.drill_problems (
      user_id, content_hash, ply, side, sfen_before, move_usi,
      loss_wp, category, verdict, note, problem_type, priority
    ) values (
      '20000000-0000-0000-0000-000000000009', repeat('9', 64), 1, 'sente',
      'startpos', '7g7f', 0.0, 'daily', 'correct', 'duplicate', 'next_move', 0.0
    ) on conflict (user_id, content_hash, ply) do nothing$$,
  'daily_limit: 上限到達後も既存drill_problemsの重複再送は通る'
);

select is(
  (select count(*)::int from public.drill_problems
    where user_id = '20000000-0000-0000-0000-000000000009'),
  500,
  'daily_limit: drill_problemsの重複再送で行数は増えない'
);

update public.drill_problems
set created_at = (date_trunc('day', now() at time zone 'Asia/Tokyo') at time zone 'Asia/Tokyo')
                 - interval '1 hour'
where user_id = '20000000-0000-0000-0000-000000000009';

select lives_ok(
  $$insert into public.drill_problems (
      user_id, content_hash, ply, side, sfen_before, move_usi,
      loss_wp, category, verdict, note, problem_type, priority
    ) values (
      '20000000-0000-0000-0000-000000000009', repeat('9', 64), 501, 'sente',
      'startpos', '7g7f', 0.0, 'daily', 'correct', 'next-day', 'next_move', 0.0
    )$$,
  'daily_limit: JST日界より前の500行は当日の上限に含まれない'
);

-- ── 日次上限（drill_attempts: 500行/日・JST日界）───────────────────────
insert into public.drill_problems (
  id, user_id, content_hash, ply, side, sfen_before, move_usi,
  loss_wp, category, verdict, note, problem_type, priority
)
values (
  '30000000-0000-0000-0000-000000000010',
  '20000000-0000-0000-0000-000000000010', repeat('a', 64), 1, 'sente',
  'startpos', '7g7f', 0.0, 'daily', 'correct', 'attempt bulk', 'next_move', 0.0
);

insert into public.drill_attempts (
  user_id, problem_id, client_attempt_id, user_move_usi, is_correct, attempted_at
)
select
  '20000000-0000-0000-0000-000000000010',
  '30000000-0000-0000-0000-000000000010',
  ('60000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
  '7g7f', true, now()
from generate_series(1, 499) as series(n);

select lives_ok(
  $$insert into public.drill_attempts (
      user_id, problem_id, client_attempt_id, user_move_usi,
      is_correct, attempted_at
    ) values (
      '20000000-0000-0000-0000-000000000010',
      '30000000-0000-0000-0000-000000000010',
      '60000000-0000-0000-0000-000000000500', '7g7f', true, now()
    )$$,
  'daily_limit: drill_attemptsは500行目までinsertできる'
);

select throws_ok(
  $$insert into public.drill_attempts (
      user_id, problem_id, client_attempt_id, user_move_usi,
      is_correct, attempted_at
    ) values (
      '20000000-0000-0000-0000-000000000010',
      '30000000-0000-0000-0000-000000000010',
      '60000000-0000-0000-0000-000000000501', '7g7f', true, now()
    )$$,
  'P0001', 'daily drill attempt limit reached',
  'daily_limit: drill_attemptsは501行目で弾かれる'
);

select lives_ok(
  $$insert into public.drill_attempts (
      user_id, problem_id, client_attempt_id, user_move_usi,
      is_correct, attempted_at
    ) values (
      '20000000-0000-0000-0000-000000000010',
      '30000000-0000-0000-0000-000000000010',
      '60000000-0000-0000-0000-000000000001', '7g7f', true, now()
    ) on conflict (user_id, client_attempt_id) do nothing$$,
  'daily_limit: 上限到達後も既存drill_attemptsの重複再送は通る'
);

select is(
  (select count(*)::int from public.drill_attempts
    where user_id = '20000000-0000-0000-0000-000000000010'),
  500,
  'daily_limit: drill_attemptsの重複再送で行数は増えない'
);

update public.drill_attempts
set created_at = (date_trunc('day', now() at time zone 'Asia/Tokyo') at time zone 'Asia/Tokyo')
                 - interval '1 hour'
where user_id = '20000000-0000-0000-0000-000000000010';

select lives_ok(
  $$insert into public.drill_attempts (
      user_id, problem_id, client_attempt_id, user_move_usi,
      is_correct, attempted_at
    ) values (
      '20000000-0000-0000-0000-000000000010',
      '30000000-0000-0000-0000-000000000010',
      '60000000-0000-0000-0000-000000000501', '7g7f', true, now()
    )$$,
  'daily_limit: JST日界より前の500件は当日の上限に含まれない'
);

reset role;
select * from finish();
rollback;
