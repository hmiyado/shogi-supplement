-- Run after analysis_generation.sql has created the isolated test schema.
BEGIN;
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', '10000000-0000-0000-0000-000000000001', true);
INSERT INTO public.uploaded_games(user_id,content_hash,moves_usi,private_enc,analysis_json)
VALUES ('10000000-0000-0000-0000-000000000001',repeat('d',64),'[]','old-owner','[]');
DO $$ BEGIN
    IF public.update_study_private_enc(repeat('d',64),'wrong','stale') THEN RAISE EXCEPTION 'stale copy accepted'; END IF;
    IF EXISTS (SELECT 1 FROM public.uploaded_games_v2 WHERE content_hash=repeat('d',64)) THEN RAISE EXCEPTION 'stale copy created'; END IF;
    IF NOT public.update_study_private_enc(repeat('d',64),'old-owner',repeat('x',20000)) THEN RAISE EXCEPTION 'copy failed'; END IF;
    IF (SELECT private_enc FROM public.uploaded_games WHERE content_hash=repeat('d',64)) <> 'old-owner' THEN RAISE EXCEPTION 'legacy changed'; END IF;
    IF public.update_study_private_enc(repeat('d',64),'old-owner','stale') THEN RAISE EXCEPTION 'stale CAS accepted'; END IF;
    IF public.update_study_private_enc(repeat('d',64),repeat('x',20000),NULL) THEN RAISE EXCEPTION 'null accepted'; END IF;
    BEGIN
        PERFORM public.update_study_private_enc(repeat('d',64),repeat('x',20000),repeat('x',65537));
        RAISE EXCEPTION 'size limit bypassed';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        INSERT INTO public.uploaded_games_v2(user_id,content_hash,moves_usi) VALUES (auth.uid(),repeat('f',64),'[]');
        RAISE EXCEPTION 'direct v2 write accepted';
    EXCEPTION WHEN insufficient_privilege THEN NULL; END;
    BEGIN
        UPDATE public.uploaded_games SET private_enc='direct';
        RAISE EXCEPTION 'legacy update permission expanded';
    EXCEPTION WHEN insufficient_privilege THEN NULL; END;
END $$;
SELECT set_config('request.jwt.claim.sub', '10000000-0000-0000-0000-000000000002', true);
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM public.uploaded_games_current WHERE content_hash=repeat('d',64)) THEN RAISE EXCEPTION 'view leaked another owner'; END IF;
    IF public.update_study_private_enc(repeat('d',64),repeat('x',20000),'stolen') THEN RAISE EXCEPTION 'cross-owner update accepted'; END IF;
END $$;
SELECT set_config('request.jwt.claim.sub', '', true);
DO $$ BEGIN
    IF public.update_study_private_enc(repeat('d',64),repeat('x',20000),'no-user') THEN RAISE EXCEPTION 'unauthenticated update accepted'; END IF;
END $$;
SET LOCAL ROLE anon;
DO $$ BEGIN
    BEGIN
        PERFORM public.update_study_private_enc(repeat('d',64),'old-owner','anon');
        RAISE EXCEPTION 'anonymous execute granted';
    EXCEPTION WHEN insufficient_privilege THEN NULL; END;
    BEGIN
        PERFORM * FROM public.uploaded_games_current;
        RAISE EXCEPTION 'anonymous select granted';
    EXCEPTION WHEN insufficient_privilege THEN NULL; END;
END $$;
ROLLBACK;
