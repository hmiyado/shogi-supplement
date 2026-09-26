-- 旧版の暗号文を上書きせず、新版側へコピーしてから比較更新する。
CREATE OR REPLACE FUNCTION public.update_study_private_enc(
    p_content_hash text, p_expected_private_enc text, p_private_enc text
) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
    owner_id uuid := auth.uid();
    affected integer;
BEGIN
    IF owner_id IS NULL OR p_private_enc IS NULL THEN RETURN false; END IF;
    PERFORM pg_advisory_xact_lock(hashtextextended(owner_id::text || ':' || p_content_hash, 0));
    IF EXISTS (SELECT 1 FROM public.deleted_analysis_generations
        WHERE user_id = owner_id AND content_hash = p_content_hash) THEN RETURN false; END IF;
    IF NOT EXISTS (SELECT 1 FROM public.uploaded_games_v2 WHERE user_id = owner_id AND content_hash = p_content_hash) THEN
        INSERT INTO public.uploaded_games_v2
        SELECT (jsonb_populate_record(NULL::public.uploaded_games_v2, to_jsonb(legacy))).*
        FROM public.uploaded_games legacy WHERE user_id = owner_id AND content_hash = p_content_hash
            AND private_enc IS NOT DISTINCT FROM p_expected_private_enc;
    END IF;
    UPDATE public.uploaded_games_v2 SET private_enc = p_private_enc
    WHERE user_id = owner_id AND content_hash = p_content_hash
        AND private_enc IS NOT DISTINCT FROM p_expected_private_enc;
    GET DIAGNOSTICS affected = ROW_COUNT;
    RETURN affected = 1;
END;
$$;
REVOKE ALL ON FUNCTION public.update_study_private_enc(text, text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.update_study_private_enc(text, text, text) TO authenticated;
