-- 解析世代の再送では派生問題・回答を再作成しない。旧方式のテーブルへは書き込まない。
CREATE FUNCTION public.replace_analysis_generation(
    p_content_hash text, p_generation uuid, p_expected_generation uuid,
    p_game jsonb, p_problems jsonb
) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
    owner_id uuid := auth.uid();
    current_generation uuid;
    prior_fingerprint bytea;
    prior_private_written boolean;
    stable_payload jsonb;
    game_row public.uploaded_games_v2;
    inserted boolean;
BEGIN
    IF owner_id IS NULL THEN RAISE EXCEPTION 'authentication required' USING ERRCODE = '42501'; END IF;
    IF (p_game->>'user_id')::uuid IS DISTINCT FROM owner_id THEN
        RAISE EXCEPTION 'owner changed' USING ERRCODE = '42501';
    END IF;
    IF p_generation IS NULL OR p_content_hash IS NULL OR p_content_hash !~ '^[0-9a-f]{64}$'
       OR p_game IS NULL OR jsonb_typeof(p_game) <> 'object'
       OR p_problems IS NULL OR jsonb_typeof(p_problems) <> 'array'
       OR jsonb_array_length(p_problems) > 10000
       OR pg_column_size(p_game) > 524288 OR pg_column_size(p_problems) > 4194304 THEN
        RAISE EXCEPTION 'invalid analysis snapshot' USING ERRCODE = '22023';
    END IF;
    -- 行がまだない初回送信も、同じ所有者・棋譜の送信同士で直列化する。
    PERFORM pg_advisory_xact_lock(hashtextextended(owner_id::text || ':' || p_content_hash, 0));
    SELECT analysis_generation INTO current_generation FROM public.uploaded_games_v2
        WHERE user_id = owner_id AND content_hash = p_content_hash FOR UPDATE;
    inserted := NOT FOUND;
    IF inserted THEN
        SELECT generation INTO current_generation FROM public.deleted_analysis_generations
            WHERE user_id = owner_id AND content_hash = p_content_hash;
    END IF;
    -- 暗号文はnonceで変わるため再送の同一性に含めない。検討は専用CASが所有する。
    stable_payload := jsonb_build_object('game', p_game - ARRAY['private_enc', 'id', 'user_id',
        'content_hash', 'created_at', 'analysis_generation'], 'problems', p_problems);
    SELECT fingerprint, initial_private_written INTO prior_fingerprint, prior_private_written FROM public.analysis_generation_receipts
        WHERE user_id = owner_id AND content_hash = p_content_hash AND generation = p_generation;
    IF FOUND THEN
        IF prior_fingerprint IS DISTINCT FROM sha256(convert_to(stable_payload::text, 'UTF8')) THEN
            RAISE EXCEPTION 'generation payload mismatch' USING ERRCODE = '22023';
        END IF;
        RETURN jsonb_build_object('status', CASE WHEN current_generation = p_generation
            THEN 'already_applied' ELSE 'superseded' END, 'generation', current_generation,
            'private_written', false, 'initial_private_written', prior_private_written);
    END IF;
    IF current_generation IS DISTINCT FROM p_expected_generation THEN
        RETURN jsonb_build_object('status', 'conflict', 'generation', current_generation,
            'private_written', false);
    END IF;
    game_row := jsonb_populate_record(NULL::public.uploaded_games_v2, p_game);
    IF inserted THEN
    INSERT INTO public.uploaded_games_v2 (
        user_id, content_hash, moves_usi, move_times, headers, result, source_place, side,
        private_enc, rating_service, rating_raw, rating_rule, rating_declared_at, user_rank,
        opponent_rank, started_at, time_control, byoyomi, estimated_rating,
        rating_sample_moves, move_count, coef_version, analysis_json, engine_meta, analysis_generation
    ) VALUES (
        owner_id, p_content_hash, game_row.moves_usi, game_row.move_times, game_row.headers,
        game_row.result, game_row.source_place, game_row.side, game_row.private_enc,
        game_row.rating_service, game_row.rating_raw, game_row.rating_rule, game_row.rating_declared_at,
        game_row.user_rank, game_row.opponent_rank, game_row.started_at, game_row.time_control,
        game_row.byoyomi, game_row.estimated_rating, game_row.rating_sample_moves, game_row.move_count,
        game_row.coef_version, game_row.analysis_json, game_row.engine_meta, p_generation
    );
    ELSE
        UPDATE public.uploaded_games_v2 SET
            moves_usi = game_row.moves_usi, move_times = game_row.move_times, headers = game_row.headers,
            result = game_row.result, source_place = game_row.source_place, side = game_row.side,
            rating_service = game_row.rating_service, rating_raw = game_row.rating_raw,
            rating_rule = game_row.rating_rule, rating_declared_at = game_row.rating_declared_at,
            user_rank = game_row.user_rank, opponent_rank = game_row.opponent_rank,
            started_at = game_row.started_at, time_control = game_row.time_control, byoyomi = game_row.byoyomi,
            estimated_rating = game_row.estimated_rating, rating_sample_moves = game_row.rating_sample_moves,
            move_count = game_row.move_count, coef_version = game_row.coef_version,
            analysis_json = game_row.analysis_json, engine_meta = game_row.engine_meta,
            analysis_generation = p_generation
        WHERE user_id = owner_id AND content_hash = p_content_hash;
    END IF;
    DELETE FROM public.drill_problems_v2 WHERE user_id = owner_id AND content_hash = p_content_hash;
    INSERT INTO public.drill_problems_v2 (user_id, content_hash, ply, side, sfen_before, move_usi,
        best_usi, loss_wp, category, verdict, note, problem_type, priority, second_usi, second_cp)
    SELECT owner_id, p_content_hash, p.ply, p.side, p.sfen_before, p.move_usi, p.best_usi,
        p.loss_wp, p.category, p.verdict, p.note, p.problem_type, p.priority, p.second_usi, p.second_cp
    FROM jsonb_populate_recordset(NULL::public.drill_problems_v2, p_problems) p;
    INSERT INTO public.analysis_generation_receipts VALUES
        (owner_id, p_content_hash, p_generation, sha256(convert_to(stable_payload::text, 'UTF8')), inserted);
    DELETE FROM public.deleted_analysis_generations WHERE user_id = owner_id AND content_hash = p_content_hash;
    RETURN jsonb_build_object('status', 'applied', 'generation', p_generation, 'private_written', inserted);
END;
$$;

CREATE FUNCTION public.record_generation_attempt(
    p_content_hash text, p_generation uuid, p_ply integer, p_attempt jsonb
) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
    owner_id uuid := auth.uid();
    target_problem_id uuid;
    attempt public.drill_attempts_v2;
BEGIN
    IF owner_id IS NULL THEN RAISE EXCEPTION 'authentication required' USING ERRCODE = '42501'; END IF;
    PERFORM pg_advisory_xact_lock(hashtextextended(owner_id::text || ':' || p_content_hash, 0));
    PERFORM 1 FROM public.uploaded_games_v2 WHERE user_id = owner_id AND content_hash = p_content_hash
        AND analysis_generation = p_generation FOR UPDATE;
    IF NOT FOUND THEN RETURN false; END IF;
    SELECT id INTO target_problem_id FROM public.drill_problems_v2
        WHERE user_id = owner_id AND content_hash = p_content_hash AND ply = p_ply;
    IF NOT FOUND THEN RETURN false; END IF;
    attempt := jsonb_populate_record(NULL::public.drill_attempts_v2, p_attempt);
    IF attempt.user_id IS DISTINCT FROM owner_id THEN
        RAISE EXCEPTION 'owner changed' USING ERRCODE = '42501';
    END IF;
    INSERT INTO public.drill_attempts_v2 (user_id, problem_id, client_attempt_id, user_move_usi,
        is_correct, loss_wp, attempted_at)
    VALUES (owner_id, target_problem_id, attempt.client_attempt_id, attempt.user_move_usi,
        attempt.is_correct, attempt.loss_wp, attempt.attempted_at)
    ON CONFLICT (user_id, client_attempt_id) DO NOTHING;
    RETURN EXISTS (SELECT 1 FROM public.drill_attempts_v2 a
        WHERE a.user_id = owner_id AND a.client_attempt_id = attempt.client_attempt_id
        AND a.problem_id = target_problem_id AND a.user_move_usi = attempt.user_move_usi
        AND a.is_correct = attempt.is_correct AND a.loss_wp IS NOT DISTINCT FROM attempt.loss_wp
        AND a.attempted_at = attempt.attempted_at);
END;
$$;

CREATE FUNCTION public.delete_analysis_generation(
    p_content_hash text, p_expected_generation uuid, p_delete_generation uuid, p_expected_user_id uuid
) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
    owner_id uuid := auth.uid();
    current_generation uuid;
BEGIN
    IF owner_id IS NULL THEN RAISE EXCEPTION 'authentication required' USING ERRCODE = '42501'; END IF;
    IF p_expected_user_id IS DISTINCT FROM owner_id THEN RAISE EXCEPTION 'owner changed' USING ERRCODE = '42501'; END IF;
    IF p_delete_generation IS NULL OR p_content_hash IS NULL OR p_content_hash !~ '^[0-9a-f]{64}$' THEN
        RAISE EXCEPTION 'invalid deletion' USING ERRCODE = '22023';
    END IF;
    PERFORM pg_advisory_xact_lock(hashtextextended(owner_id::text || ':' || p_content_hash, 0));
    SELECT generation INTO current_generation FROM public.deleted_analysis_generations
        WHERE user_id = owner_id AND content_hash = p_content_hash;
    IF FOUND THEN RETURN current_generation = p_delete_generation; END IF;
    SELECT analysis_generation INTO current_generation FROM public.uploaded_games_v2
        WHERE user_id = owner_id AND content_hash = p_content_hash FOR UPDATE;
    IF current_generation IS DISTINCT FROM p_expected_generation THEN RETURN false; END IF;
    -- 存在しない棋譜も削除要求と競合する初回送信から保護する。
    INSERT INTO public.deleted_analysis_generations VALUES (owner_id, p_content_hash, p_delete_generation);
    DELETE FROM public.uploaded_games_v2 WHERE user_id = owner_id AND content_hash = p_content_hash;
    RETURN true;
END;
$$;

CREATE FUNCTION public.get_analysis_sync_state(p_content_hash text) RETURNS jsonb
LANGUAGE sql SECURITY DEFINER SET search_path = '' AS $$
    SELECT coalesce(
        (SELECT jsonb_build_object('generation', analysis_generation, 'deleted', false)
         FROM public.uploaded_games_v2 WHERE user_id = auth.uid() AND content_hash = p_content_hash),
        (SELECT jsonb_build_object('generation', generation, 'deleted', true)
         FROM public.deleted_analysis_generations WHERE user_id = auth.uid() AND content_hash = p_content_hash),
        jsonb_build_object('generation', NULL, 'deleted', false));
$$;

REVOKE INSERT, UPDATE, DELETE ON public.uploaded_games_v2 FROM authenticated;
REVOKE INSERT, UPDATE, DELETE ON public.drill_problems_v2, public.drill_attempts_v2 FROM authenticated;
REVOKE ALL ON FUNCTION public.replace_analysis_generation(text, uuid, uuid, jsonb, jsonb) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.record_generation_attempt(text, uuid, integer, jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.replace_analysis_generation(text, uuid, uuid, jsonb, jsonb) TO authenticated;
GRANT EXECUTE ON FUNCTION public.record_generation_attempt(text, uuid, integer, jsonb) TO authenticated;
REVOKE ALL ON FUNCTION public.delete_analysis_generation(text, uuid, uuid, uuid) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.get_analysis_sync_state(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.delete_analysis_generation(text, uuid, uuid, uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_analysis_sync_state(text) TO authenticated;

-- 新版で削除した棋譜を、残っている旧版のコピーから復活させない。
CREATE POLICY own_rows ON public.deleted_analysis_generations FOR SELECT TO authenticated USING (user_id = auth.uid());
GRANT SELECT ON public.deleted_analysis_generations TO authenticated;
CREATE VIEW public.uploaded_games_current WITH (security_invoker = true) AS
    SELECT * FROM public.uploaded_games_v2
    UNION ALL
    SELECT legacy.*, NULL::uuid AS analysis_generation FROM public.uploaded_games legacy
    WHERE NOT EXISTS (SELECT 1 FROM public.uploaded_games_v2 current_game
        WHERE current_game.user_id = legacy.user_id AND current_game.content_hash = legacy.content_hash)
      AND NOT EXISTS (SELECT 1 FROM public.deleted_analysis_generations deleted
        WHERE deleted.user_id = legacy.user_id AND deleted.content_hash = legacy.content_hash);
REVOKE ALL ON public.uploaded_games_current FROM PUBLIC, anon, authenticated;
GRANT SELECT ON public.uploaded_games_current TO authenticated;
