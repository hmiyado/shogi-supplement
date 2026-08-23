-- pgTAP: RLSポリシーと権限（明示GRANT）のテスト。
-- ロール切り替え（authenticated/anon＋JWTクレーム偽装）で
-- 「本人の行しか見えない・書けない」「GRANTの無い操作はそもそも拒否される」を検証する。
begin;
create extension if not exists pgtap with schema extensions;

select plan(22);

insert into auth.users (id, instance_id, aud, role, created_at, updated_at)
values
  ('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', now(), now()),
  ('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', now(), now());

-- ワーカー専用テーブルに行を仕込んでおく（postgresとして）
insert into public.analysis_jobs (user_id, moves_hash, moves_usi)
values ('10000000-0000-0000-0000-000000000001', 'rls-hash', '["7g7f"]');

-- ── user 1: 本人の行の作成 ───────────────────────────────────────────────
set local role authenticated;
set local request.jwt.claims to '{"sub": "10000000-0000-0000-0000-000000000001", "role": "authenticated"}';

select lives_ok(
  $$insert into public.uploaded_games (user_id, content_hash, moves_usi)
    values ('10000000-0000-0000-0000-000000000001', 'rls-own', '["7g7f"]')$$,
  'uploaded_games: 本人の行はinsertできる'
);

select throws_ok(
  $$insert into public.uploaded_games (user_id, content_hash, moves_usi)
    values ('10000000-0000-0000-0000-000000000002', 'rls-forged', '["7g7f"]')$$,
  '42501', null,
  'uploaded_games: 他人のuser_idを騙ったinsertは弾かれる'
);

select lives_ok(
  $$insert into public.user_transfer_secrets (user_id, key_auth_hash)
    values ('10000000-0000-0000-0000-000000000001', 'hash-own')$$,
  'user_transfer_secrets: 本人の行はinsertできる'
);

select throws_ok(
  $$insert into public.user_transfer_secrets (user_id, key_auth_hash)
    values ('10000000-0000-0000-0000-000000000002', 'hash-forged')$$,
  '42501', null,
  'user_transfer_secrets: 他人のuser_idを騙ったinsertは弾かれる'
);

-- ── user 2: 他人の行は見えない ───────────────────────────────────────────
set local request.jwt.claims to '{"sub": "10000000-0000-0000-0000-000000000002", "role": "authenticated"}';

select is(
  (select count(*)::int from public.uploaded_games),
  0,
  'uploaded_games: 他人の行はselectで見えない'
);

select is(
  (select count(*)::int from public.user_transfer_secrets),
  0,
  'user_transfer_secrets: 他人の行はselectで見えない'
);

-- ── GRANTの無い操作は拒否される（RLS以前の一段目の防壁）──────────────────
-- アップロード行の変更機能は提供しない（値の変更手段が無い）
select throws_ok(
  $$update public.uploaded_games set side = 'sente'
    where user_id = '10000000-0000-0000-0000-000000000001'$$,
  '42501', null,
  'uploaded_games: UPDATE権限自体が無い'
);

-- ── uploaded_games: DELETE（棋譜削除機能。20260822160000でGRANT）─────────
-- 引き続きuser2としてログイン中。本人の行のみ消せることを確認する。
select lives_ok(
  $$insert into public.uploaded_games (user_id, content_hash, moves_usi)
    values ('10000000-0000-0000-0000-000000000002', 'rls-own-2', '["7g7f"]')$$,
  'uploaded_games: user2は自分の行をinsertできる（DELETE検証の準備）'
);

select lives_ok(
  $$delete from public.uploaded_games
    where user_id = '10000000-0000-0000-0000-000000000002'
      and content_hash = 'rls-own-2'$$,
  'uploaded_games: 本人の行はDELETEできる'
);

select is(
  (select count(*)::int from public.uploaded_games where content_hash = 'rls-own-2'),
  0,
  'uploaded_games: DELETEした行は消える'
);

select lives_ok(
  $$delete from public.uploaded_games
    where user_id = '10000000-0000-0000-0000-000000000001'
      and content_hash = 'rls-own'$$,
  'uploaded_games: 他人の行を指定したDELETEはRLSで対象0件になり例外にならない'
);

-- 確認自体もuser2のRLS越しだと他人の行が見えず0件になってしまうため、
-- 行の生死はRLSをバイパスするpostgresロールで確認する
reset role;
select is(
  (select count(*)::int from public.uploaded_games where content_hash = 'rls-own'),
  1,
  'uploaded_games: 他人の行はDELETEで消えない（RLSにより対象外）'
);
set local role authenticated;
set local request.jwt.claims to '{"sub": "10000000-0000-0000-0000-000000000002", "role": "authenticated"}';

-- ワーカー専用テーブルはクライアントから読み書きとも不可
select throws_ok(
  $$select count(*) from public.analysis_jobs$$,
  '42501', null,
  'analysis_jobs: クライアントからselectできない'
);

select throws_ok(
  $$insert into public.analysis_jobs (user_id, moves_hash)
    values ('10000000-0000-0000-0000-000000000002', 'rls-client-insert')$$,
  '42501', null,
  'analysis_jobs: クライアントからinsertできない'
);

-- user_transfer_secretsのUPDATE検証のため、他人（user1）行との対比用にuser2の行を用意する
select lives_ok(
  $$insert into public.user_transfer_secrets (user_id, key_auth_hash)
    values ('10000000-0000-0000-0000-000000000002', 'hash-own-2')$$,
  'user_transfer_secrets: user2は自分の行をinsertできる（UPDATE検証の準備）'
);

-- ── user_transfer_secrets: UPDATE（引き継ぎコード再生成。20260815130000でGRANT）
set local request.jwt.claims to '{"sub": "10000000-0000-0000-0000-000000000001", "role": "authenticated"}';

select lives_ok(
  $$update public.user_transfer_secrets set key_auth_hash = 'hash-rotated'
    where user_id = '10000000-0000-0000-0000-000000000001'$$,
  'user_transfer_secrets: 本人の行はUPDATEできる（コード再生成）'
);

select is(
  (select key_auth_hash from public.user_transfer_secrets
    where user_id = '10000000-0000-0000-0000-000000000001'),
  'hash-rotated',
  'user_transfer_secrets: UPDATEした値が反映される'
);

select lives_ok(
  $$update public.user_transfer_secrets set key_auth_hash = 'hash-forged'
    where user_id = '10000000-0000-0000-0000-000000000002'$$,
  'user_transfer_secrets: 他人の行を指定したUPDATEはRLSで対象0件になり例外にならない'
);

-- 確認自体もuser1のRLS越しだと他人の行が見えず値を読めないため、
-- 値の変更有無はRLSをバイパスするpostgresロールで確認する
reset role;
select is(
  (select key_auth_hash from public.user_transfer_secrets
    where user_id = '10000000-0000-0000-0000-000000000002'),
  'hash-own-2',
  'user_transfer_secrets: 他人の行の値はUPDATEで変わらない（RLSにより対象外）'
);
set local role authenticated;
set local request.jwt.claims to '{"sub": "10000000-0000-0000-0000-000000000001", "role": "authenticated"}';

select throws_ok(
  $$insert into public.user_transfer_secrets (user_id, key_auth_hash)
    values ('10000000-0000-0000-0000-000000000001', 'hash-second')$$,
  '23505', null,
  'user_transfer_secrets: 2回目のinsertはunique_violation（行の追加は増やさない前提）'
);

-- ── anon: 何もできない ───────────────────────────────────────────────────
set local role anon;
set local request.jwt.claims to '{"role": "anon"}';

select throws_ok(
  $$select count(*) from public.uploaded_games$$,
  '42501', null,
  'uploaded_games: anonはselect権限自体が無い'
);

select throws_ok(
  $$select public.delete_user()$$,
  '42501', null,
  'delete_user: anonは実行できない'
);

reset role;
select * from finish();
rollback;
