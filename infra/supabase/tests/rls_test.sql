-- pgTAP: RLSポリシーと権限（明示GRANT）のテスト。
-- ロール切り替え（authenticated/anon＋JWTクレーム偽装）で
-- 「本人の行しか見えない・書けない」「GRANTの無い操作はそもそも拒否される」を検証する。
begin;
create extension if not exists pgtap with schema extensions;

select plan(14);

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
-- アップロード行の変更・削除機能は提供しない（行の消去はdelete_userのcascade）
select throws_ok(
  $$update public.uploaded_games set side = 'sente'
    where user_id = '10000000-0000-0000-0000-000000000001'$$,
  '42501', null,
  'uploaded_games: UPDATE権限自体が無い'
);

select throws_ok(
  $$delete from public.uploaded_games
    where user_id = '10000000-0000-0000-0000-000000000002'$$,
  '42501', null,
  'uploaded_games: DELETE権限自体が無い'
);

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

-- ── user_transfer_secrets はinsert固定（Why not upsertの前提を権限で固定）──
set local request.jwt.claims to '{"sub": "10000000-0000-0000-0000-000000000001", "role": "authenticated"}';

select throws_ok(
  $$update public.user_transfer_secrets set key_auth_hash = 'hash-rotated'
    where user_id = '10000000-0000-0000-0000-000000000001'$$,
  '42501', null,
  'user_transfer_secrets: UPDATE権限自体が無い（本人でも更新不可）'
);

select throws_ok(
  $$insert into public.user_transfer_secrets (user_id, key_auth_hash)
    values ('10000000-0000-0000-0000-000000000001', 'hash-second')$$,
  '23505', null,
  'user_transfer_secrets: 2回目のinsertはunique_violation（登録済み扱いの前提）'
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
