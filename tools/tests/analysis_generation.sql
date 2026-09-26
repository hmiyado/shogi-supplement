-- Isolated PostgreSQL fixture; transaction rolls back all test objects.
\set ON_ERROR_STOP on
BEGIN;
CREATE ROLE authenticated;
CREATE ROLE anon;
CREATE ROLE service_role;
CREATE SCHEMA auth;
CREATE TABLE auth.users (id uuid PRIMARY KEY);
CREATE FUNCTION auth.uid() RETURNS uuid LANGUAGE sql STABLE AS
$$ SELECT nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$;
GRANT USAGE ON SCHEMA auth, public TO authenticated, anon;
create table public.uploaded_games (
  id                  uuid primary key default gen_random_uuid(),
  user_id             uuid not null references auth.users(id) on delete cascade,
  content_hash        text not null,
  moves_usi           jsonb not null,
  -- 1手ごとの消費秒（null要素=テンポ不明局面）
  move_times          jsonb,
  -- ホワイトリスト済みヘッダのみ（KifuDecomposer.HEADER_WHITELIST）
  headers             jsonb,
  -- 終局理由（投了・時間切れ等）
  result              text,
  -- 出典サービスの正規化値のみ（KifuSource.wireValue。対局URL等はprivate_enc側）
  source_place        text,
  -- アップロードしたユーザーが指した側（sente/gote。null=未申告）。
  -- 対局者名は平文に持たないため、この列が無いと申告レートをどちらの側の
  -- 成績と対応付けるか判別できない
  side                text,
  -- version(1B)+nonce(12B)+AES-256-GCM暗号文のBase64。
  -- Why not bytea: PostgRESTのbytea往復（hex表現）の実環境検証が済むまで、
  -- クライアント側だけで完結するtext+Base64を使う。数百バイト/局なので冗長化は無視できる
  private_enc         text,
  -- ユーザーの申告棋力（サービス名・サービス上のraw値・ルール）。較正データの中核軸
  rating_service      text,
  rating_raw          integer,
  rating_rule         text,
  -- KIF記載の段級。headersの先手段級/後手段級をside基準でユーザー側/相手側に
  -- 割り付けた検索用の複製（headersが正本）
  user_rank           text,
  opponent_rank       text,
  -- 対局開始日時（分丸め済み・JSTとして解釈）。headersの開始日時の検索用複製
  started_at          timestamptz,
  -- 時間設定（headersの持ち時間・秒読みの検索用複製。時間設定は棋力統計の主要な交絡軸）
  time_control        text,
  byoyomi             text,
  -- 解析からの推定棋力（申告のrating_rawとは別物）
  estimated_rating    integer,
  rating_sample_moves integer,
  move_count          integer,
  coef_version        text,
  analysis_json       jsonb,
  created_at          timestamptz not null default now(),
  unique (user_id, content_hash),
  -- 行サイズの安全弁。アップロードはRLS越しの直接insertで、ワーカーのような
  -- サーバー側検証を通らないため、異常な巨大行はDB制約で弾く
  -- （正常値は moves_usi ~1KB・analysis_json 数KB・private_enc ~1KB）
  constraint uploaded_games_size_limits check (
    pg_column_size(moves_usi) <= 51200
    and (move_times is null or pg_column_size(move_times) <= 51200)
    and (headers is null or pg_column_size(headers) <= 10240)
    and (analysis_json is null or pg_column_size(analysis_json) <= 262144)
    and (private_enc is null or length(private_enc) <= 65536)
  )
);

ALTER TABLE public.uploaded_games ENABLE ROW LEVEL SECURITY;
CREATE POLICY own_rows ON public.uploaded_games FOR ALL TO authenticated
USING (user_id = auth.uid()) WITH CHECK (user_id = auth.uid());
GRANT SELECT, INSERT, DELETE ON public.uploaded_games TO authenticated;
\ir ../../infra/supabase/migrations/20260823090000_create_drill_sync.sql
\ir ../../infra/supabase/migrations/20260914090000_add_rating_declared_at.sql
\ir ../../infra/supabase/migrations/20260917100000_add_uploaded_games_engine_meta.sql
\ir ../../infra/supabase/migrations/20260919090000_create_versioned_sync.sql
\ir ../../infra/supabase/migrations/20260919130000_update_study_private_enc.sql
\ir ../../infra/supabase/migrations/20260919150000_analysis_generation_rpc.sql
INSERT INTO auth.users VALUES ('10000000-0000-0000-0000-000000000001'), ('10000000-0000-0000-0000-000000000002');
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', '10000000-0000-0000-0000-000000000001', true);
DO $$
DECLARE
    hash text := repeat('a', 64);
    g1 uuid := '20000000-0000-0000-0000-000000000001';
    g2 uuid := '20000000-0000-0000-0000-000000000002';
    g3 uuid := '20000000-0000-0000-0000-000000000003';
    game jsonb := '{"user_id":"10000000-0000-0000-0000-000000000001","moves_usi":["7g7f"],"private_enc":"original","analysis_json":[]}';
    problems jsonb := '[{"ply":1,"side":"sente","sfen_before":"startpos","move_usi":"7g7f","best_usi":"2g2f","loss_wp":0.3,"category":"test","verdict":"TARGET","note":"","problem_type":"test","priority":1}]';
    answer jsonb := jsonb_build_object('user_id','10000000-0000-0000-0000-000000000001','client_attempt_id','30000000-0000-0000-0000-000000000001',
        'user_move_usi','2g2f','is_correct',true,'attempted_at',now());
    result jsonb;
BEGIN
    result := public.replace_analysis_generation(hash,g1,NULL,game,problems);
    IF result->>'status' <> 'applied' OR NOT (result->>'private_written')::boolean THEN
        RAISE EXCEPTION 'initial snapshot not applied';
    END IF;
    IF NOT public.record_generation_attempt(hash,g1,1,answer) THEN RAISE EXCEPTION 'answer rejected'; END IF;
    IF public.record_generation_attempt(hash,g1,1,answer || '{"user_move_usi":"7g7f"}') THEN
        RAISE EXCEPTION 'changed answer retry accepted';
    END IF;
    IF NOT public.update_study_private_enc(hash,'original','edited') THEN RAISE EXCEPTION 'study CAS denied'; END IF;
    result := public.replace_analysis_generation(hash,g1,NULL,game,problems);
    IF result->>'status' <> 'already_applied' OR (SELECT count(*) FROM public.drill_attempts_v2) <> 1 THEN
        RAISE EXCEPTION 'replay deleted answers';
    END IF;
    IF NOT (result->>'initial_private_written')::boolean OR (result->>'private_written')::boolean THEN
        RAISE EXCEPTION 'lost initial reply cannot confirm original private write';
    END IF;
    BEGIN
        PERFORM public.replace_analysis_generation(hash,g1,NULL,game || '{"coef_version":"different"}',problems);
        RAISE EXCEPTION 'changed replay accepted';
    EXCEPTION WHEN invalid_parameter_value THEN NULL; END;
    result := public.replace_analysis_generation(hash,g2,g1,game,problems);
    IF result->>'status' <> 'applied' OR (result->>'private_written')::boolean
        OR (SELECT count(*) FROM public.drill_attempts_v2) <> 0
        OR (SELECT private_enc FROM public.uploaded_games_v2) <> 'edited' THEN
        RAISE EXCEPTION 'replacement corrupted snapshot or study';
    END IF;
    IF public.record_generation_attempt(hash,g1,1,answer) THEN RAISE EXCEPTION 'stale answer accepted'; END IF;
    IF NOT public.record_generation_attempt(hash,g2,1,answer) THEN RAISE EXCEPTION 'current answer rejected'; END IF;
    result := public.replace_analysis_generation(hash,g1,NULL,game,problems);
    IF result->>'status' <> 'superseded'
        OR (SELECT count(*) FROM public.drill_attempts_v2) <> 1 THEN RAISE EXCEPTION 'old replay mutated current'; END IF;
    IF public.replace_analysis_generation(hash,g3,g1,game,'[]')->>'status' <> 'conflict' THEN
        RAISE EXCEPTION 'stale expected generation accepted';
    END IF;
    BEGIN
        PERFORM public.replace_analysis_generation(hash,g3,g2,game,'[{"ply":1}]');
        RAISE EXCEPTION 'invalid problem accepted';
    EXCEPTION WHEN not_null_violation THEN NULL; END;
    IF (SELECT analysis_generation FROM public.uploaded_games_v2) <> g2
        OR (SELECT count(*) FROM public.drill_attempts_v2) <> 1 THEN RAISE EXCEPTION 'rollback failed'; END IF;
    result := public.replace_analysis_generation(hash,g3,g2,game,'[]');
    IF result->>'status' <> 'applied'
        OR (SELECT count(*) FROM public.drill_problems_v2) <> 0 THEN RAISE EXCEPTION 'zero problem replacement failed'; END IF;
    BEGIN
        UPDATE public.uploaded_games_v2 SET private_enc = 'direct';
        RAISE EXCEPTION 'direct DML accepted';
    EXCEPTION WHEN insufficient_privilege THEN NULL; END;
    BEGIN
        DELETE FROM public.analysis_generation_receipts;
        RAISE EXCEPTION 'receipt DML accepted';
    EXCEPTION WHEN insufficient_privilege THEN NULL; END;
END $$;
SELECT set_config('request.jwt.claim.sub', '10000000-0000-0000-0000-000000000002', true);
DO $$ BEGIN
    BEGIN
        PERFORM public.replace_analysis_generation(repeat('c',64), gen_random_uuid(), NULL,
            '{"user_id":"10000000-0000-0000-0000-000000000001","moves_usi":[]}', '[]');
        RAISE EXCEPTION 'account switch accepted another owner payload';
    EXCEPTION WHEN insufficient_privilege THEN NULL; END;
    IF public.record_generation_attempt(repeat('a',64),'20000000-0000-0000-0000-000000000003',1,'{}') THEN
        RAISE EXCEPTION 'cross owner answer accepted';
    END IF;
    IF public.update_study_private_enc(repeat('a',64),'edited','stolen') THEN
        RAISE EXCEPTION 'cross owner study accepted';
    END IF;
END $$;
RESET ROLE;
ROLLBACK;
\echo Analysis generation assertions passed
